import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PrototypeStateService } from '../../core/services/prototype-state.service';

export type AdminView = 'users' | 'settings';

interface AdminUser {
  id: number;
  name: string;
  email: string;
  initials: string;
  primaryWork: string;
  secondaryWork: string;
  status: 'Active' | 'Pending' | 'Suspended';
  lastActive: string;
  mfa: boolean;
}

interface RoleDefinition {
  name: string;
  description: string;
  users: number;
  tone: string;
  permissions: string[];
}

@Component({
  selector: 'sf-admin-workspace',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-workspace.component.html',
  styleUrl: './admin-workspace.component.css'
})
export class AdminWorkspaceComponent implements OnChanges, OnDestroy, OnInit {
  @Input({ required: true }) view: AdminView = 'users';
  @Input() tenantLabel = 'Selected tenant';
  @Input() searchQuery = '';

  readonly prototype = inject(PrototypeStateService);

  roleFilter = 'ALL';
  statusFilter = 'ALL';
  toastMessage = '';
  activeSettingsSection = 'organization';
  private toastTimer?: number;

  organizationName = '';
  timezone = 'Asia/Kolkata';
  currency = 'INR';
  dateFormat = 'DD MMM YYYY';
  forecastHorizon = 30;
  safetyStockDays = 14;
  approvalThreshold = 100000;
  routeObjective = 'Balanced cost & carbon';
  co2Target = 12;
  wasteTarget = 18;
  vehicleUtilization = 85;
  riskAlerts = true;
  purchaseApprovals = true;
  transferApprovals = true;
  dailyDigest = false;
  mfaRequired = false;
  sessionTimeout = 60;
  auditRetention = 365;

  users: AdminUser[] = [
    { id: 1, name: 'Veyjval', email: 'veyjval@stockflow.ai', initials: 'V', primaryWork: 'Software development', secondaryWork: 'Project Coordinator', status: 'Active', lastActive: 'Just now', mfa: true },
    { id: 2, name: 'Arnab', email: 'arnab@stockflow.ai', initials: 'A', primaryWork: 'Mobile development', secondaryWork: 'Project Coordinator', status: 'Active', lastActive: 'Current sprint', mfa: true },
    { id: 3, name: 'Shreyas', email: 'shreyas@stockflow.ai', initials: 'S', primaryWork: 'PPT presentation', secondaryWork: 'Software dev', status: 'Active', lastActive: 'Current sprint', mfa: false },
    { id: 4, name: 'Pari', email: 'pari@stockflow.ai', initials: 'P', primaryWork: 'Presenter', secondaryWork: 'Research', status: 'Active', lastActive: 'Current sprint', mfa: true },
    { id: 5, name: 'Thavanesh', email: 'thavanesh@stockflow.ai', initials: 'T', primaryWork: 'Presenter', secondaryWork: 'Research', status: 'Active', lastActive: 'Current sprint', mfa: false },
    { id: 6, name: 'Dharmanshu', email: 'dharmanshu@stockflow.ai', initials: 'D', primaryWork: 'Database Engineer', secondaryWork: 'Assist developer', status: 'Active', lastActive: 'Current sprint', mfa: true }
  ];

  readonly roles: RoleDefinition[] = [
    { name: 'Software development', description: 'Builds and integrates the StockFlow web platform', users: 1, tone: 'violet', permissions: ['Core application', 'Feature integration', 'Technical delivery'] },
    { name: 'Mobile development', description: 'Owns the mobile experience and app integration', users: 1, tone: 'blue', permissions: ['Mobile UI', 'API integration', 'Device testing'] },
    { name: 'PPT presentation', description: 'Creates the SIH pitch deck and product story', users: 1, tone: 'teal', permissions: ['Pitch deck', 'Demo narrative', 'Visual assets'] },
    { name: 'Presenter', description: 'Presents the solution, research and impact', users: 2, tone: 'orange', permissions: ['Live pitch', 'Research', 'Jury Q&A'] },
    { name: 'Database Engineer', description: 'Owns data design, integrity and developer support', users: 1, tone: 'slate', permissions: ['Database', 'Data security', 'Developer support'] }
  ];

  readonly settingsSections = [
    { id: 'organization', label: 'Organization', icon: 'O' },
    { id: 'planning', label: 'Planning policies', icon: 'P' },
    { id: 'sustainability', label: 'Sustainability', icon: 'S' },
    { id: 'notifications', label: 'Notifications', icon: 'N' },
    { id: 'security', label: 'Security', icon: 'L' },
    { id: 'integrations', label: 'Integrations', icon: 'I' }
  ];

  ngOnInit(): void {
    this.users.forEach(user => {
      Object.assign(user, this.prototype.recordPatch<AdminUser>('users', String(user.id)));
    });
    Object.assign(this, this.prototype.settingsSnapshot<{
      organizationName: string;
      timezone: string;
      currency: string;
      dateFormat: string;
      forecastHorizon: number;
      safetyStockDays: number;
      approvalThreshold: number;
      routeObjective: string;
      co2Target: number;
      wasteTarget: number;
      vehicleUtilization: number;
      riskAlerts: boolean;
      purchaseApprovals: boolean;
      transferApprovals: boolean;
      dailyDigest: boolean;
      mfaRequired: boolean;
      sessionTimeout: number;
      auditRetention: number;
    }>());
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['tenantLabel'] && (!this.organizationName || changes['tenantLabel'].firstChange)) {
      this.organizationName = this.tenantLabel;
    }
    if (changes['view']) {
      this.roleFilter = 'ALL';
      this.statusFilter = 'ALL';
    }
  }

  ngOnDestroy(): void {
    if (this.toastTimer) window.clearTimeout(this.toastTimer);
  }

  get pageCopy(): { eyebrow: string; title: string; description: string; action: string } {
    return this.view === 'users'
      ? { eyebrow: 'ACCESS GOVERNANCE', title: 'Users & Roles', description: 'Control team access, warehouse scope and role permissions across your StockFlow workspace.', action: 'Invite user' }
      : { eyebrow: 'WORKSPACE CONTROL', title: 'Settings', description: 'Configure planning policies, sustainability targets, alerts and security for your organization.', action: 'Save changes' };
  }

  userRoles(): string[] {
    return [...new Set(this.users.map(user => user.primaryWork))].sort();
  }

  statuses(): string[] {
    return ['Active', 'Pending', 'Suspended'];
  }

  filteredUsers(): AdminUser[] {
    const query = this.searchQuery.trim().toLowerCase();
    return this.users.filter(user => {
      const matchesSearch = !query || [user.name, user.email, user.primaryWork, user.secondaryWork]
        .some(value => value.toLowerCase().includes(query));
      const matchesRole = this.roleFilter === 'ALL' || user.primaryWork === this.roleFilter;
      const matchesStatus = this.statusFilter === 'ALL' || user.status === this.statusFilter;
      return matchesSearch && matchesRole && matchesStatus;
    });
  }

  activeUsers(): number {
    return this.users.filter(user => user.status === 'Active').length;
  }

  mfaCoverage(): number {
    return Math.round(this.users.filter(user => user.mfa).length / Math.max(this.users.length, 1) * 100);
  }

  mfaEnabledUsers(): number {
    return this.users.filter(user => user.mfa).length;
  }

  statusClass(status: string): string {
    return status.toLowerCase();
  }

  inviteUser(): void {
    this.prototype.addActivity({
      module: 'Users & Roles',
      title: 'Invite flow opened',
      detail: 'The prototype recorded the action without sending an external invitation.',
      tone: 'info'
    });
    this.showToast('Invite flow recorded. No external invitation was sent.');
  }

  changeRole(user: AdminUser, role: string): void {
    user.primaryWork = role;
    this.prototype.patchRecord('users', String(user.id), { primaryWork: role }, {
      module: 'Users & Roles',
      title: `${user.name}'s role updated`,
      detail: `Primary work changed to ${role}. The change is saved in this browser.`,
      tone: 'success'
    });
    this.showToast(`${user.name}'s role was updated and saved.`);
  }

  toggleUserStatus(user: AdminUser): void {
    user.status = user.status === 'Active' ? 'Suspended' : 'Active';
    this.prototype.patchRecord('users', String(user.id), { status: user.status }, {
      module: 'Users & Roles',
      title: `${user.name} is now ${user.status}`,
      detail: `Workspace access status was changed and retained for the prototype.`,
      tone: user.status === 'Active' ? 'success' : 'warning'
    });
    this.showToast(`${user.name} is now ${user.status}; the change was saved.`);
  }

  saveSettings(): void {
    this.prototype.saveSettings({
      organizationName: this.organizationName,
      timezone: this.timezone,
      currency: this.currency,
      dateFormat: this.dateFormat,
      forecastHorizon: this.forecastHorizon,
      safetyStockDays: this.safetyStockDays,
      approvalThreshold: this.approvalThreshold,
      routeObjective: this.routeObjective,
      co2Target: this.co2Target,
      wasteTarget: this.wasteTarget,
      vehicleUtilization: this.vehicleUtilization,
      riskAlerts: this.riskAlerts,
      purchaseApprovals: this.purchaseApprovals,
      transferApprovals: this.transferApprovals,
      dailyDigest: this.dailyDigest,
      mfaRequired: this.mfaRequired,
      sessionTimeout: this.sessionTimeout,
      auditRetention: this.auditRetention
    }, {
      module: 'Settings',
      title: 'Workspace settings saved',
      detail: `Planning, sustainability and security preferences for ${this.organizationName} were saved locally.`,
      tone: 'success'
    });
    this.showToast('Settings saved and will remain after refresh.');
  }

  selectSettingsSection(section: string): void {
    this.activeSettingsSection = section;
  }

  private showToast(message: string): void {
    this.toastMessage = message;
    if (this.toastTimer) window.clearTimeout(this.toastTimer);
    this.toastTimer = window.setTimeout(() => this.toastMessage = '', 3500);
  }
}
