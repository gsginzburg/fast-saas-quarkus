import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ToolbarModule } from 'primeng/toolbar';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { DividerModule } from 'primeng/divider';
import { ConfirmationService, MessageService } from 'primeng/api';
import { AuthService } from '../../core/services/auth.service';
import { ClusterApiService } from '../../core/services/cluster.service';
import { FullContext, TestRecord } from '../../core/models/api.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ToolbarModule,
    CardModule,
    TableModule,
    ButtonModule,
    TagModule,
    DialogModule,
    InputTextModule,
    InputNumberModule,
    ToastModule,
    ConfirmDialogModule,
    ProgressSpinnerModule,
    DividerModule
  ],
  providers: [MessageService, ConfirmationService],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit {
  auth = inject(AuthService);
  private clusterApi = inject(ClusterApiService);
  private confirmationService = inject(ConfirmationService);
  private messageService = inject(MessageService);

  context = signal<FullContext | null>(null);
  testRecords = signal<TestRecord[]>([]);
  loadingContext = signal(false);
  loadingRecords = signal(false);
  contextError = signal<string | null>(null);
  showCreateDialog = false;
  saving = signal(false);
  newRecord: { name: string; description: string; value: number | null } = {
    name: '',
    description: '',
    value: null
  };

  ngOnInit(): void {
    this.loadContext();
    this.loadRecords();
  }

  loadContext(): void {
    this.loadingContext.set(true);
    this.contextError.set(null);
    this.clusterApi.getContext().subscribe({
      next: (ctx) => {
        this.context.set(ctx);
        this.loadingContext.set(false);
      },
      error: (err) => {
        this.loadingContext.set(false);
        const msg = err?.error?.error || err?.message || 'Failed to load context';
        this.contextError.set(msg);
        this.messageService.add({
          severity: 'error',
          summary: 'Context Error',
          detail: msg
        });
      }
    });
  }

  loadRecords(): void {
    this.loadingRecords.set(true);
    this.clusterApi.getTestRecords().subscribe({
      next: (records) => {
        this.testRecords.set(records);
        this.loadingRecords.set(false);
      },
      error: (err) => {
        this.loadingRecords.set(false);
        const msg = err?.error?.error || err?.message || 'Failed to load records';
        this.messageService.add({
          severity: 'error',
          summary: 'Load Error',
          detail: msg
        });
      }
    });
  }

  openCreateDialog(): void {
    this.newRecord = { name: '', description: '', value: null };
    this.showCreateDialog = true;
  }

  closeCreateDialog(): void {
    this.showCreateDialog = false;
  }

  createRecord(): void {
    if (!this.newRecord.name || this.saving()) {
      return;
    }

    this.saving.set(true);

    const payload: { name: string; description?: string; value?: number } = {
      name: this.newRecord.name
    };
    if (this.newRecord.description) {
      payload.description = this.newRecord.description;
    }
    if (this.newRecord.value !== null && this.newRecord.value !== undefined) {
      payload.value = this.newRecord.value;
    }

    this.clusterApi.createTestRecord(payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.showCreateDialog = false;
        this.messageService.add({
          severity: 'success',
          summary: 'Created',
          detail: `Record "${this.newRecord.name}" created successfully`
        });
        this.loadRecords();
      },
      error: (err) => {
        this.saving.set(false);
        const msg = err?.error?.error || err?.message || 'Failed to create record';
        this.messageService.add({
          severity: 'error',
          summary: 'Create Error',
          detail: msg
        });
      }
    });
  }

  confirmDelete(record: TestRecord): void {
    this.confirmationService.confirm({
      message: `Are you sure you want to delete "<strong>${record.name}</strong>"?`,
      header: 'Confirm Delete',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Delete',
      rejectLabel: 'Cancel',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        this.clusterApi.deleteTestRecord(record.id).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: 'Deleted',
              detail: `Record "${record.name}" deleted`
            });
            this.loadRecords();
          },
          error: (err) => {
            const msg = err?.error?.error || err?.message || 'Failed to delete record';
            this.messageService.add({
              severity: 'error',
              summary: 'Delete Error',
              detail: msg
            });
          }
        });
      }
    });
  }
}
