import { Injectable, computed, signal } from '@angular/core';

export type PrototypeTone = 'info' | 'success' | 'warning' | 'critical';

export interface PrototypeActivity {
  id: string;
  module: string;
  title: string;
  detail: string;
  tone: PrototypeTone;
  timestamp: string;
}

interface PrototypeState {
  version: 1;
  recordPatches: Record<string, Record<string, Record<string, unknown>>>;
  settings: Record<string, unknown>;
  activities: PrototypeActivity[];
  lastUpdatedAt: string | null;
}

const EMPTY_STATE: PrototypeState = {
  version: 1,
  recordPatches: {},
  settings: {},
  activities: [],
  lastUpdatedAt: null
};

@Injectable({ providedIn: 'root' })
export class PrototypeStateService {
  private readonly storageKey = 'stockflowPrototypeStateV1';
  private readonly state = signal<PrototypeState>(this.load());

  readonly activities = computed(() => this.state().activities);
  readonly lastUpdatedAt = computed(() => this.state().lastUpdatedAt);
  readonly changeCount = computed(() => {
    const recordChanges = Object.values(this.state().recordPatches)
      .reduce((sum, collection) => sum + Object.keys(collection).length, 0);
    return recordChanges + Object.keys(this.state().settings).length;
  });

  recordPatch<T extends object>(collection: string, id: string): Partial<T> {
    return (this.state().recordPatches[collection]?.[id] ?? {}) as Partial<T>;
  }

  isChanged(collection: string, id: string): boolean {
    return Boolean(this.state().recordPatches[collection]?.[id]);
  }

  settingsSnapshot<T extends object>(): Partial<T> {
    return this.state().settings as Partial<T>;
  }

  patchRecord(
    collection: string,
    id: string,
    patch: Record<string, unknown>,
    activity: Omit<PrototypeActivity, 'id' | 'timestamp'>
  ): void {
    const current = this.state();
    const timestamp = new Date().toISOString();
    const next: PrototypeState = {
      ...current,
      recordPatches: {
        ...current.recordPatches,
        [collection]: {
          ...(current.recordPatches[collection] ?? {}),
          [id]: {
            ...(current.recordPatches[collection]?.[id] ?? {}),
            ...patch
          }
        }
      },
      activities: [this.activity(activity, timestamp), ...current.activities].slice(0, 40),
      lastUpdatedAt: timestamp
    };
    this.commit(next);
  }

  saveSettings(
    settings: Record<string, unknown>,
    activity: Omit<PrototypeActivity, 'id' | 'timestamp'>
  ): void {
    const current = this.state();
    const timestamp = new Date().toISOString();
    this.commit({
      ...current,
      settings: { ...current.settings, ...settings },
      activities: [this.activity(activity, timestamp), ...current.activities].slice(0, 40),
      lastUpdatedAt: timestamp
    });
  }

  addActivity(activity: Omit<PrototypeActivity, 'id' | 'timestamp'>): void {
    const current = this.state();
    const timestamp = new Date().toISOString();
    this.commit({
      ...current,
      activities: [this.activity(activity, timestamp), ...current.activities].slice(0, 40),
      lastUpdatedAt: timestamp
    });
  }

  reset(): void {
    const timestamp = new Date().toISOString();
    this.commit({
      ...EMPTY_STATE,
      activities: [this.activity({
        module: 'Demo control',
        title: 'Prototype reset',
        detail: 'All locally saved demo changes were restored to their initial values.',
        tone: 'info'
      }, timestamp)],
      lastUpdatedAt: timestamp
    });
  }

  private activity(activity: Omit<PrototypeActivity, 'id' | 'timestamp'>, timestamp: string): PrototypeActivity {
    return {
      ...activity,
      id: `ACT-${Date.now()}-${Math.floor(Math.random() * 1000)}`,
      timestamp
    };
  }

  private commit(next: PrototypeState): void {
    this.state.set(next);
    localStorage.setItem(this.storageKey, JSON.stringify(next));
  }

  private load(): PrototypeState {
    try {
      const raw = localStorage.getItem(this.storageKey);
      if (!raw) return { ...EMPTY_STATE };
      const parsed = JSON.parse(raw) as Partial<PrototypeState>;
      if (parsed.version !== 1) return { ...EMPTY_STATE };
      return {
        version: 1,
        recordPatches: parsed.recordPatches ?? {},
        settings: parsed.settings ?? {},
        activities: parsed.activities ?? [],
        lastUpdatedAt: parsed.lastUpdatedAt ?? null
      };
    } catch {
      return { ...EMPTY_STATE };
    }
  }
}
