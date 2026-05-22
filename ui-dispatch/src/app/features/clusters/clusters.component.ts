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
import { TagModule } from 'primeng/tag';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ClusterService } from '../../core/services/cluster.service';
import { Cluster } from '../../core/models/api.model';

type TagSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

@Component({
  selector: 'app-clusters',
  standalone: true,
  imports: [
    CommonModule,
    DatePipe,
    FormsModule,
    TableModule,
    ButtonModule,
    DialogModule,
    InputTextModule,
    TagModule,
    ConfirmDialogModule,
    ToastModule,
    TooltipModule
  ],
  providers: [ConfirmationService, MessageService],
  templateUrl: './clusters.component.html'
})
export class ClustersComponent implements OnInit {
  private clusterService = inject(ClusterService);
  private confirmationService = inject(ConfirmationService);
  private messageService = inject(MessageService);

  // Signals
  clusters = signal<Cluster[]>([]);
  loading = signal(false);
  saving = signal(false);
  totalRecords = signal(0);
  upgradingClusterId = signal<string | null>(null);

  pageSize = 20;

  // Create dialog
  showCreateDialog = false;
  form = { name: '', url: '', apiUrl: '' };

  // Edit dialog
  showEditDialog = false;
  editingCluster: Cluster | null = null;
  editForm = { name: '', url: '', apiUrl: '' };

  ngOnInit(): void {
    this.loadClusters(0);
  }

  loadClusters(page: number): void {
    this.loading.set(true);
    this.clusterService.getClusters(page, this.pageSize).subscribe({
      next: (result) => {
        this.clusters.set(result.content);
        this.totalRecords.set(result.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to load clusters'
        });
      }
    });
  }

  onLazyLoad(event: TableLazyLoadEvent): void {
    const page = Math.floor((event.first ?? 0) / (event.rows ?? this.pageSize));
    this.loadClusters(page);
  }

  openCreateDialog(): void {
    this.form = { name: '', url: '', apiUrl: '' };
    this.showCreateDialog = true;
  }

  createCluster(): void {
    if (!this.form.name.trim() || !this.form.url.trim()) return;
    this.saving.set(true);
    this.clusterService.createCluster(this.form.name.trim(), this.form.url.trim(), this.form.apiUrl.trim()).subscribe({
      next: () => {
        this.showCreateDialog = false;
        this.saving.set(false);
        this.messageService.add({
          severity: 'success',
          summary: 'Success',
          detail: 'Cluster created successfully'
        });
        this.loadClusters(0);
      },
      error: (err) => {
        this.saving.set(false);
        const e = err as { error?: { error?: string } };
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: e?.error?.error || 'Failed to create cluster'
        });
      }
    });
  }

  openEditDialog(cluster: Cluster): void {
    this.editingCluster = cluster;
    this.editForm = { name: cluster.name, url: cluster.url, apiUrl: cluster.apiUrl || '' };
    this.showEditDialog = true;
  }

  updateCluster(): void {
    if (!this.editingCluster) return;
    if (!this.editForm.name.trim() || !this.editForm.url.trim()) return;
    this.saving.set(true);
    this.clusterService
      .updateCluster(this.editingCluster.id, this.editForm.name.trim(), this.editForm.url.trim(), this.editForm.apiUrl.trim())
      .subscribe({
        next: () => {
          this.showEditDialog = false;
          this.saving.set(false);
          this.messageService.add({
            severity: 'success',
            summary: 'Updated',
            detail: 'Cluster updated successfully'
          });
          this.loadClusters(0);
        },
        error: (err) => {
          this.saving.set(false);
          const e = err as { error?: { error?: string } };
          this.messageService.add({
            severity: 'error',
            summary: 'Error',
            detail: e?.error?.error || 'Failed to update cluster'
          });
        }
      });
  }

  upgradeAll(cluster: Cluster): void {
    this.upgradingClusterId.set(cluster.id);
    this.clusterService.upgradeAll(cluster.id).subscribe({
      next: () => {
        this.upgradingClusterId.set(null);
        this.messageService.add({
          severity: 'success',
          summary: 'Success',
          detail: `All tenant schemas on "${cluster.name}" upgraded`
        });
      },
      error: (err) => {
        this.upgradingClusterId.set(null);
        const e = err as { error?: { error?: string } };
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: e?.error?.error || 'Failed to upgrade cluster schemas'
        });
      }
    });
  }

  confirmDelete(cluster: Cluster): void {
    this.confirmationService.confirm({
      message: `Are you sure you want to delete cluster "<strong>${cluster.name}</strong>"? This action cannot be undone.`,
      header: 'Confirm Delete',
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        this.clusterService.deleteCluster(cluster.id).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: 'Deleted',
              detail: `Cluster "${cluster.name}" deleted`
            });
            this.loadClusters(0);
          },
          error: (err) => {
            const e = err as { error?: { error?: string } };
            this.messageService.add({
              severity: 'error',
              summary: 'Error',
              detail: e?.error?.error || 'Failed to delete cluster'
            });
          }
        });
      }
    });
  }
}
