/*
 * Copyright 2026 Gary Ginzburg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule, DatePipe } from '@angular/common';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService, MessageService } from 'primeng/api';
import { AuthService } from '../../core/services/auth.service';
import { TenantService } from '../../core/services/tenant.service';
import { ClusterService } from '../../core/services/cluster.service';
import { Tenant, Cluster, TenantMembership } from '../../core/models/api.model';

type TagSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

interface StatusOption {
  label: string;
  value: string;
}

@Component({
  selector: 'app-tenants',
  standalone: true,
  imports: [
    CommonModule,
    DatePipe,
    FormsModule,
    TableModule,
    ButtonModule,
    DialogModule,
    InputTextModule,
    SelectModule,
    TagModule,
    ConfirmDialogModule,
    ToastModule,
    TooltipModule
  ],
  providers: [ConfirmationService, MessageService],
  templateUrl: './tenants.component.html'
})
export class TenantsComponent implements OnInit {
  private authService = inject(AuthService);
  private tenantService = inject(TenantService);
  private clusterService = inject(ClusterService);
  private confirmationService = inject(ConfirmationService);
  private messageService = inject(MessageService);

  // Signals
  tenants = signal<Tenant[]>([]);
  clusters = signal<Cluster[]>([]);
  loading = signal(false);
  saving = signal(false);
  totalRecords = signal(0);
  tenantUsers = signal<TenantMembership[]>([]);
  loadingUsers = signal(false);
  savingUser = signal(false);
  openingTenantId = signal<string | null>(null);
  provisioningTenantId = signal<string | null>(null);
  upgradingTenantId = signal<string | null>(null);

  pageSize = 20;

  // Create dialog
  showCreateDialog = false;
  newTenantName = '';
  selectedClusterId = '';

  // Edit dialog
  showEditDialog = false;
  editingTenant: Tenant | null = null;
  editStatus = '';
  editClusterId = '';

  // Users dialog
  showUsersDialog = false;
  selectedTenant: Tenant | null = null;
  addUserId = '';
  addUserRole = 'MEMBER';

  statusOptions: StatusOption[] = [
    { label: 'Active', value: 'ACTIVE' },
    { label: 'Inactive', value: 'INACTIVE' },
    { label: 'Archived', value: 'ARCHIVED' }
  ];

  roleOptions = ['MEMBER', 'ADMIN', 'OWNER'];

  ngOnInit(): void {
    this.loadClusters();
    this.loadTenants(0);
  }

  loadClusters(): void {
    this.clusterService.getAllClusters().subscribe({
      next: (c) => this.clusters.set(c),
      error: () =>
        this.messageService.add({
          severity: 'warn',
          summary: 'Warning',
          detail: 'Could not load clusters'
        })
    });
  }

  loadTenants(page: number): void {
    this.loading.set(true);
    this.tenantService.getTenants(page, this.pageSize).subscribe({
      next: (result) => {
        this.tenants.set(result.content);
        this.totalRecords.set(result.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to load tenants'
        });
      }
    });
  }

  onLazyLoad(event: TableLazyLoadEvent): void {
    const page = Math.floor((event.first ?? 0) / (event.rows ?? this.pageSize));
    this.loadTenants(page);
  }

  openTenant(tenant: Tenant): void {
    if (!tenant.clusterUrl) {
      this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Tenant has no cluster URL configured' });
      return;
    }
    this.openingTenantId.set(tenant.id);
    this.authService.scopeToTenant(tenant.id).subscribe({
      next: (scopedToken) => {
        this.openingTenantId.set(null);
        window.open(`${tenant.clusterUrl}?token=${scopedToken}`, '_blank');
      },
      error: () => {
        this.openingTenantId.set(null);
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to generate tenant token' });
      }
    });
  }

  provisionTenant(tenant: Tenant): void {
    this.provisioningTenantId.set(tenant.id);
    this.tenantService.provisionTenant(tenant.id).subscribe({
      next: () => {
        this.provisioningTenantId.set(null);
        this.messageService.add({ severity: 'success', summary: 'Provisioned', detail: `Schema for "${tenant.name}" provisioned` });
      },
      error: (err) => {
        this.provisioningTenantId.set(null);
        const e = err as { error?: { error?: string } };
        this.messageService.add({ severity: 'error', summary: 'Error', detail: e?.error?.error || 'Provisioning failed' });
      }
    });
  }

  upgradeTenant(tenant: Tenant): void {
    this.upgradingTenantId.set(tenant.id);
    this.tenantService.upgradeTenant(tenant.id).subscribe({
      next: () => {
        this.upgradingTenantId.set(null);
        this.messageService.add({ severity: 'success', summary: 'Upgraded', detail: `Schema for "${tenant.name}" upgraded` });
      },
      error: (err) => {
        this.upgradingTenantId.set(null);
        const e = err as { error?: { error?: string } };
        this.messageService.add({ severity: 'error', summary: 'Error', detail: e?.error?.error || 'Upgrade failed' });
      }
    });
  }

  openCreateDialog(): void {
    this.newTenantName = '';
    this.selectedClusterId = '';
    this.showCreateDialog = true;
  }

  createTenant(): void {
    if (!this.newTenantName.trim() || !this.selectedClusterId) return;
    this.saving.set(true);
    this.tenantService.createTenant(this.newTenantName.trim(), this.selectedClusterId).subscribe({
      next: () => {
        this.showCreateDialog = false;
        this.saving.set(false);
        this.messageService.add({
          severity: 'success',
          summary: 'Success',
          detail: 'Tenant created successfully'
        });
        this.loadTenants(0);
      },
      error: (err) => {
        this.saving.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: err?.error?.error || 'Failed to create tenant'
        });
      }
    });
  }

  openEditDialog(tenant: Tenant): void {
    this.editingTenant = tenant;
    this.editStatus = tenant.status;
    this.editClusterId = tenant.clusterId || '';
    this.showEditDialog = true;
  }

  updateTenant(): void {
    if (!this.editingTenant) return;
    this.saving.set(true);

    const statusChanged = this.editStatus !== this.editingTenant.status;
    const clusterChanged =
      this.editClusterId && this.editClusterId !== this.editingTenant.clusterId;

    const id = this.editingTenant.id;

    if (statusChanged && clusterChanged) {
      // Chain both calls
      this.tenantService.updateStatus(id, this.editStatus).subscribe({
        next: () => {
          this.tenantService.assignCluster(id, this.editClusterId).subscribe({
            next: () => this.onEditSuccess(),
            error: (err) => this.onEditError(err)
          });
        },
        error: (err) => this.onEditError(err)
      });
    } else if (statusChanged) {
      this.tenantService.updateStatus(id, this.editStatus).subscribe({
        next: () => this.onEditSuccess(),
        error: (err) => this.onEditError(err)
      });
    } else if (clusterChanged) {
      this.tenantService.assignCluster(id, this.editClusterId).subscribe({
        next: () => this.onEditSuccess(),
        error: (err) => this.onEditError(err)
      });
    } else {
      this.saving.set(false);
      this.showEditDialog = false;
    }
  }

  private onEditSuccess(): void {
    this.showEditDialog = false;
    this.saving.set(false);
    this.messageService.add({
      severity: 'success',
      summary: 'Updated',
      detail: 'Tenant updated successfully'
    });
    this.loadTenants(0);
  }

  private onEditError(err: unknown): void {
    this.saving.set(false);
    const e = err as { error?: { error?: string } };
    this.messageService.add({
      severity: 'error',
      summary: 'Error',
      detail: e?.error?.error || 'Failed to update tenant'
    });
  }

  confirmDelete(tenant: Tenant): void {
    this.confirmationService.confirm({
      message: `Are you sure you want to delete tenant "<strong>${tenant.name}</strong>"? This action cannot be undone.`,
      header: 'Confirm Delete',
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        this.tenantService.deleteTenant(tenant.id).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: 'Deleted',
              detail: `Tenant "${tenant.name}" deleted`
            });
            this.loadTenants(0);
          },
          error: (err) => {
            const e = err as { error?: { error?: string } };
            this.messageService.add({
              severity: 'error',
              summary: 'Error',
              detail: e?.error?.error || 'Failed to delete tenant'
            });
          }
        });
      }
    });
  }

  openUsersDialog(tenant: Tenant): void {
    this.selectedTenant = tenant;
    this.addUserId = '';
    this.addUserRole = 'MEMBER';
    this.showUsersDialog = true;
    this.loadTenantUsers(tenant.id);
  }

  loadTenantUsers(tenantId: string): void {
    this.loadingUsers.set(true);
    this.tenantService.getTenantUsers(tenantId).subscribe({
      next: (users) => {
        this.tenantUsers.set(users);
        this.loadingUsers.set(false);
      },
      error: () => {
        this.loadingUsers.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to load tenant users'
        });
      }
    });
  }

  assignUser(): void {
    if (!this.selectedTenant || !this.addUserId.trim()) return;
    this.savingUser.set(true);
    this.tenantService
      .assignUser(this.selectedTenant.id, this.addUserId.trim(), this.addUserRole)
      .subscribe({
        next: () => {
          this.savingUser.set(false);
          this.addUserId = '';
          this.messageService.add({
            severity: 'success',
            summary: 'Added',
            detail: 'User added to tenant'
          });
          this.loadTenantUsers(this.selectedTenant!.id);
        },
        error: (err) => {
          this.savingUser.set(false);
          const e = err as { error?: { error?: string } };
          this.messageService.add({
            severity: 'error',
            summary: 'Error',
            detail: e?.error?.error || 'Failed to add user'
          });
        }
      });
  }

  removeUserFromTenant(membership: TenantMembership): void {
    if (!this.selectedTenant) return;
    this.confirmationService.confirm({
      message: `Remove user from tenant "${this.selectedTenant.name}"?`,
      header: 'Confirm',
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        this.tenantService
          .removeUser(this.selectedTenant!.id, membership.userId)
          .subscribe({
            next: () => {
              this.messageService.add({
                severity: 'success',
                summary: 'Removed',
                detail: 'User removed from tenant'
              });
              this.loadTenantUsers(this.selectedTenant!.id);
            },
            error: (err) => {
              const e = err as { error?: { error?: string } };
              this.messageService.add({
                severity: 'error',
                summary: 'Error',
                detail: e?.error?.error || 'Failed to remove user'
              });
            }
          });
      }
    });
  }

  statusSeverity(status: string): TagSeverity {
    switch (status) {
      case 'ACTIVE':
        return 'success';
      case 'INACTIVE':
        return 'warn';
      case 'ARCHIVED':
        return 'danger';
      default:
        return 'secondary';
    }
  }
}
