import {
  AfterViewInit,
  Component,
  ElementRef,
  Input,
  OnDestroy,
  ViewChild
} from '@angular/core';

const hexToRgb = (hex: string) => {
  const clean = hex.replace('#', '').trim();
  if (!/^[0-9a-fA-F]{6}$/.test(clean)) return null;
  return {
    r: parseInt(clean.slice(0, 2), 16),
    g: parseInt(clean.slice(2, 4), 16),
    b: parseInt(clean.slice(4, 6), 16)
  };
};

const mixRgb = (from: any, to: any, amount: number) => ({
  r: Math.round(from.r + (to.r - from.r) * amount),
  g: Math.round(from.g + (to.g - from.g) * amount),
  b: Math.round(from.b + (to.b - from.b) * amount)
});

const rgbToCss = (rgb: any) => `rgb(${rgb.r}, ${rgb.g}, ${rgb.b})`;

const clamp = (value: number, min: number, max: number) =>
  Math.min(Math.max(value, min), max);

const easeOutCubic = (t: number) => 1 - Math.pow(1 - t, 3);

const resolveFontSize = (
  value: string | number,
  container: HTMLElement,
  fontWeight: string | number,
  fontFamily: string
) => {
  if (typeof value === 'number') return value;

  const probe = document.createElement('span');
  probe.textContent = 'M';
  probe.style.position = 'absolute';
  probe.style.visibility = 'hidden';
  probe.style.pointerEvents = 'none';
  probe.style.fontSize = value;
  probe.style.fontWeight = String(fontWeight);
  probe.style.fontFamily = fontFamily;
  container.appendChild(probe);
  const size = parseFloat(window.getComputedStyle(probe).fontSize) || 96;
  probe.remove();
  return size;
};

const waitForFonts = async (font: string) => {
  if (!('fonts' in document)) return;

  try {
    await document.fonts.load(font);
  } catch {}

  await document.fonts.ready;
};

@Component({
  selector: 'sf-particle-text',
  standalone: true,
  template: `
    <div
      #containerRef
      class="particle-text {{ className }}"
      [attr.aria-label]="text"
    >
      <canvas #canvasRef class="particle-text__canvas" aria-hidden="true"></canvas>
      <span class="particle-text__sr">{{ text }}</span>
    </div>
  `,
  styleUrls: ['./particle-text.component.css']
})
export class ParticleTextComponent implements AfterViewInit, OnDestroy {
  @Input() text: string = 'StockFlow AI';
  @Input() particleSize: number = 2;
  @Input() density: number = 4;
  @Input() color: string = '#ffffff';
  @Input() highlightColor: string = '#8b5cf6';
  @Input() scatter: number = 180;
  @Input() gatherDuration: number = 1600;
  @Input() stagger: number = 420;
  @Input() pointerRepel: number = 40;
  @Input() repelRadius: number = 120;
  @Input() idleDrift: number = 0.7;
  @Input() trigger: 'hover' | 'click' | 'mount' = 'mount';
  @Input() fontSize: string | number = 'clamp(3rem, 12vw, 8rem)';
  @Input() fontWeight: string | number = 800;
  @Input() fontFamily: string = 'inherit';
  @Input() glow: boolean = true;
  @Input() className: string = '';

  @ViewChild('containerRef') containerRef!: ElementRef<HTMLDivElement>;
  @ViewChild('canvasRef') canvasRef!: ElementRef<HTMLCanvasElement>;

  private particles: any[] = [];
  private animationFrame: number | null = null;
  private resizeFrame: number | null = null;
  private buildId: number = 0;
  private gathering: boolean = false;
  private gatherStart: number = 0;
  private reducedMotion: boolean = false;
  private width: number = 0;
  private height: number = 0;
  private dpr: number = 1;
  private ctx: CanvasRenderingContext2D | null = null;

  private pointer = {
    active: false,
    x: 0,
    y: 0,
    smoothX: 0,
    smoothY: 0
  };

  private resizeObserver: ResizeObserver | null = null;
  private reduceMotionQuery: MediaQueryList | null = null;

  ngAfterViewInit() {
    if (typeof window === 'undefined') return;

    const container = this.containerRef.nativeElement;
    const canvas = this.canvasRef.nativeElement;
    if (!container || !canvas) return;

    this.ctx = canvas.getContext('2d');
    if (!this.ctx) return;

    this.reduceMotionQuery = window.matchMedia?.('(prefers-reduced-motion: reduce)') || null;
    this.reducedMotion = this.reduceMotionQuery?.matches ?? false;

    this.reduceMotionQuery?.addEventListener('change', this.handleReduceMotionChange);
    canvas.addEventListener('pointerenter', this.handlePointerEnter);
    canvas.addEventListener('pointermove', this.handlePointerMove);
    canvas.addEventListener('pointerleave', this.handlePointerLeave);
    canvas.addEventListener('click', this.handleClick);

    this.resizeObserver = new ResizeObserver(() => this.queueSample());
    this.resizeObserver.observe(container);

    this.sampleText();
  }

  ngOnDestroy() {
    this.buildId += 1;
    this.resizeObserver?.disconnect();
    this.reduceMotionQuery?.removeEventListener('change', this.handleReduceMotionChange);
    
    const canvas = this.canvasRef?.nativeElement;
    if (canvas) {
      canvas.removeEventListener('pointerenter', this.handlePointerEnter);
      canvas.removeEventListener('pointermove', this.handlePointerMove);
      canvas.removeEventListener('pointerleave', this.handlePointerLeave);
      canvas.removeEventListener('click', this.handleClick);
    }

    if (this.animationFrame !== null) window.cancelAnimationFrame(this.animationFrame);
    if (this.resizeFrame !== null) window.cancelAnimationFrame(this.resizeFrame);
  }

  private startGather = (fromScatter: boolean = true) => {
    if (!this.particles.length) return;

    const now = performance.now();
    const spread = this.reducedMotion ? 0 : this.scatter;

    this.particles.forEach(particle => {
      if (fromScatter) {
        const angle = particle.seed * Math.PI * 2;
        const distance = spread * (0.35 + particle.depth * 0.75);
        particle.x =
          particle.targetX +
          Math.cos(angle) * distance +
          (particle.depth - 0.5) * spread * 0.55;
        particle.y =
          particle.targetY +
          Math.sin(angle) * distance +
          (particle.seed - 0.5) * spread * 0.55;
      }

      particle.startX = particle.x;
      particle.startY = particle.y;
      particle.delay = this.reducedMotion ? 0 : particle.seed * this.stagger;
    });

    this.gatherStart = now;
    this.gathering = true;
  };

  private drawParticle = (particle: any) => {
    if (!this.ctx) return;
    const size = particle.size;
    this.ctx.fillStyle = particle.color;

    if (size <= 2.1) {
      this.ctx.fillRect(particle.x - size / 2, particle.y - size / 2, size, size);
      return;
    }

    this.ctx.beginPath();
    this.ctx.arc(particle.x, particle.y, size / 2, 0, Math.PI * 2);
    this.ctx.fill();
  };

  private render = (now: number) => {
    if (!this.ctx) return;
    this.ctx.clearRect(0, 0, this.width, this.height);

    if (this.glow && !this.reducedMotion) {
      this.ctx.shadowBlur = this.particleSize * 3;
      this.ctx.shadowColor = this.highlightColor;
    } else {
      this.ctx.shadowBlur = 0;
    }

    this.pointer.smoothX += (this.pointer.x - this.pointer.smoothX) * 0.18;
    this.pointer.smoothY += (this.pointer.y - this.pointer.smoothY) * 0.18;

    let complete = true;

    this.particles.forEach(particle => {
      let baseX = particle.targetX;
      let baseY = particle.targetY;
      let progress = 1;

      if (this.gathering) {
        const local =
          (now - this.gatherStart - particle.delay) /
          Math.max(1, this.reducedMotion ? 1 : this.gatherDuration);
        progress = clamp(local, 0, 1);
        const eased = easeOutCubic(progress);
        baseX = particle.startX + (particle.targetX - particle.startX) * eased;
        baseY = particle.startY + (particle.targetY - particle.startY) * eased;
        if (progress < 1) complete = false;
      } else if (!this.reducedMotion && this.idleDrift > 0) {
        const driftTime = now * 0.001;
        baseX += Math.sin(driftTime * 0.9 + particle.seed * 10) * this.idleDrift * particle.depth;
        baseY += Math.cos(driftTime * 0.75 + particle.depth * 10) * this.idleDrift * particle.depth;
      }

      if (this.pointer.active && !this.reducedMotion && this.pointerRepel > 0 && this.repelRadius > 0) {
        const dx = baseX - this.pointer.smoothX;
        const dy = baseY - this.pointer.smoothY;
        const distance = Math.hypot(dx, dy);
        if (distance > 0 && distance < this.repelRadius) {
          const force = Math.pow(1 - distance / this.repelRadius, 2) * this.pointerRepel;
          baseX += (dx / distance) * force;
          baseY += (dy / distance) * force;
        }
      }

      const follow = this.reducedMotion ? 1 : 0.22;
      particle.x += (baseX - particle.x) * follow;
      particle.y += (baseY - particle.y) * follow;

      this.ctx!.globalAlpha = clamp(0.35 + progress * 0.65, 0, 1);
      this.drawParticle(particle);
    });

    this.ctx.globalAlpha = 1;
    this.ctx.shadowBlur = 0;

    if (this.gathering && complete) {
      this.gathering = false;
    }

    this.animationFrame = window.requestAnimationFrame(this.render);
  };

  private ensureRenderLoop = () => {
    if (this.animationFrame === null) {
      this.animationFrame = window.requestAnimationFrame(this.render);
    }
  };

  private sampleText = async () => {
    const currentBuild = ++this.buildId;
    const container = this.containerRef.nativeElement;
    const canvas = this.canvasRef.nativeElement;
    const rect = container.getBoundingClientRect();
    this.width = Math.floor(rect.width);
    this.height = Math.floor(rect.height);

    if (this.width <= 0 || this.height <= 0) return;

    this.dpr = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = Math.max(1, Math.floor(this.width * this.dpr));
    canvas.height = Math.max(1, Math.floor(this.height * this.dpr));
    canvas.style.width = '100%';
    canvas.style.height = '100%';
    this.ctx!.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);

    const computed = window.getComputedStyle(container);
    const resolvedFamily =
      this.fontFamily === 'inherit' ? computed.fontFamily || 'sans-serif' : this.fontFamily;
    let resolvedSize = resolveFontSize(this.fontSize, container, this.fontWeight, resolvedFamily);
    let font = `${this.fontWeight} ${resolvedSize}px ${resolvedFamily}`;

    await waitForFonts(font);
    if (currentBuild !== this.buildId) return;

    const offscreen = document.createElement('canvas');
    const offCtx = offscreen.getContext('2d', { willReadFrequently: true });
    if (!offCtx) return;

    const content = String(this.text || ' ');
    const maxTextWidth = this.width * 0.92;
    offCtx.font = font;
    let metrics = offCtx.measureText(content);
    const measuredWidth = Math.max(1, metrics.width);
    if (measuredWidth > maxTextWidth) {
      resolvedSize = Math.max(18, resolvedSize * (maxTextWidth / measuredWidth));
      font = `${this.fontWeight} ${resolvedSize}px ${resolvedFamily}`;
      await waitForFonts(font);
      if (currentBuild !== this.buildId) return;
      offCtx.font = font;
      metrics = offCtx.measureText(content);
    }

    const left = Math.ceil(metrics.actualBoundingBoxLeft || 0);
    const right = Math.ceil(metrics.actualBoundingBoxRight || metrics.width);
    const ascent = Math.ceil(metrics.actualBoundingBoxAscent || resolvedSize * 0.78);
    const descent = Math.ceil(metrics.actualBoundingBoxDescent || resolvedSize * 0.22);
    const padding = Math.max(12, Math.ceil(resolvedSize * 0.08));
    const textWidth = Math.max(1, left + right);
    const textHeight = Math.max(1, ascent + descent);

    offscreen.width = textWidth + padding * 2;
    offscreen.height = textHeight + padding * 2;
    offCtx.clearRect(0, 0, offscreen.width, offscreen.height);
    offCtx.font = font;
    offCtx.textAlign = 'left';
    offCtx.textBaseline = 'alphabetic';
    offCtx.fillStyle = '#ffffff';
    offCtx.fillText(content, padding - left, padding + ascent);

    const imageData = offCtx.getImageData(0, 0, offscreen.width, offscreen.height);
    const targets = [];
    const step = Math.max(2, Math.floor(this.density));

    for (let y = 0; y < offscreen.height; y += step) {
      for (let x = 0; x < offscreen.width; x += step) {
        const alpha = imageData.data[(y * offscreen.width + x) * 4 + 3];
        if (alpha > 40) {
          targets.push({
            x: this.width / 2 - offscreen.width / 2 + x,
            y: this.height / 2 - offscreen.height / 2 + y,
            alpha: alpha / 255
          });
        }
      }
    }

    const maxParticles = Math.max(900, Math.min(5200, Math.floor((this.width * this.height) / 90)));
    const stride = Math.max(1, Math.ceil(targets.length / maxParticles));
    const baseRgb = hexToRgb(this.color);
    const highlightRgb = hexToRgb(this.highlightColor);
    const selected = targets.filter((_, index) => index % stride === 0);

    this.particles = selected.map((target, index) => {
      const seed = ((index * 9301 + 49297) % 233280) / 233280;
      const depth = 0.45 + (((index * 233 + 97) % 1000) / 1000) * 0.9;
      const blend =
        baseRgb && highlightRgb
          ? clamp(target.x / Math.max(1, this.width) + (seed - 0.5) * 0.35, 0, 1)
          : 0;
      const particleColor =
        baseRgb && highlightRgb ? rgbToCss(mixRgb(baseRgb, highlightRgb, blend)) : this.color;
      const angle = seed * Math.PI * 2;
      const distance = (this.reducedMotion ? 0 : this.scatter) * (0.35 + depth * 0.75);
      const startX = target.x + Math.cos(angle) * distance + (seed - 0.5) * this.scatter * 0.45;
      const startY = target.y + Math.sin(angle) * distance + (depth - 0.9) * this.scatter * 0.45;

      return {
        x: this.reducedMotion ? target.x : startX,
        y: this.reducedMotion ? target.y : startY,
        startX,
        startY,
        targetX: target.x,
        targetY: target.y,
        size: Math.max(0.6, this.particleSize * (0.75 + target.alpha * 0.45)),
        color: particleColor,
        seed,
        depth,
        delay: seed * this.stagger
      };
    });

    this.pointer.x = this.width / 2;
    this.pointer.y = this.height / 2;
    this.pointer.smoothX = this.pointer.x;
    this.pointer.smoothY = this.pointer.y;

    if (this.reducedMotion) {
      this.particles.forEach(particle => {
        particle.x = particle.targetX;
        particle.y = particle.targetY;
        particle.startX = particle.targetX;
        particle.startY = particle.targetY;
        particle.delay = 0;
      });
      this.gathering = false;
    } else {
      this.startGather(false);
    }

    this.ensureRenderLoop();
  };

  private queueSample = () => {
    if (this.resizeFrame) window.cancelAnimationFrame(this.resizeFrame);
    this.resizeFrame = window.requestAnimationFrame(this.sampleText);
  };

  private handlePointerMove = (event: PointerEvent) => {
    const rect = this.canvasRef.nativeElement.getBoundingClientRect();
    this.pointer.x = event.clientX - rect.left;
    this.pointer.y = event.clientY - rect.top;
    this.pointer.active = true;
  };

  private handlePointerLeave = () => {
    this.pointer.active = false;
  };

  private handlePointerEnter = (event: PointerEvent) => {
    this.handlePointerMove(event);
    if (this.trigger === 'hover') this.startGather(true);
  };

  private handleClick = () => {
    if (this.trigger === 'click') this.startGather(true);
  };

  private handleReduceMotionChange = (event: MediaQueryListEvent) => {
    this.reducedMotion = event.matches;
    this.sampleText();
  };
}
