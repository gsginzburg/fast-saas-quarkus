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

import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule, DatePipe } from '@angular/common';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService, MessageService } from 'primeng/api';
import { UserService, CreateUserRequest } from '../../core/services/user.service';
import { AppUser } from '../../core/models/api.model';

type TagSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

interface SelectOption {
  label: string;
  value: string;
}

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [
    CommonModule,
    DatePipe,
    FormsModule,
    TableModule,
    ButtonModule,
    DialogModule,
    InputTextModule,
    PasswordModule,
    SelectModule,
    TagModule,
    ConfirmDialogModule,
    ToastModule,
    TooltipModule
  ],
  providers: [ConfirmationService, MessageService],
  templateUrl: './users.component.html'
})
export class UsersComponent implements OnInit {
  private userService = inject(UserService);
  private confirmationService = inject(ConfirmationService);
  private messageService = inject(MessageService);

  // Signals
  users = signal<AppUser[]>([]);
  loading = signal(false);
  saving = signal(false);
  totalRecords = signal(0);

  pageSize = 20;

  // Create dialog
  showCreateDialog = false;
  form: CreateUserRequest & { firstName: string; lastName: string } = {
    email: '',
    password: '',
    firstName: '',
    lastName: '',
    userType: 'TENANT'
  };

  userTypeOptions: SelectOption[] = [
    { label: 'Tenant User', value: 'TENANT' },
    { label: 'Backoffice Admin', value: 'BACKOFFICE' }
  ];

  ngOnInit(): void {
    this.loadUsers(0);
  }

  loadUsers(page: number): void {
    this.loading.set(true);
    this.userService.getUsers(page, this.pageSize).subscribe({
      next: (result) => {
        this.users.set(result.content);
        this.totalRecords.set(result.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to load users'
        });
      }
    });
  }

  onLazyLoad(event: TableLazyLoadEvent): void {
    const page = Math.floor((event.first ?? 0) / (event.rows ?? this.pageSize));
    this.loadUsers(page);
  }

  openCreateDialog(): void {
    this.form = {
      email: '',
      password: '',
      firstName: '',
      lastName: '',
      userType: 'TENANT'
    };
    this.showCreateDialog = true;
  }

  createUser(): void {
    if (!this.form.email.trim() || !this.form.password.trim()) return;
    this.saving.set(true);

    const payload: CreateUserRequest = {
      email: this.form.email.trim(),
      password: this.form.password,
      userType: this.form.userType
    };
    if (this.form.firstName.trim()) payload.firstName = this.form.firstName.trim();
    if (this.form.lastName.trim()) payload.lastName = this.form.lastName.trim();

    this.userService.createUser(payload).subscribe({
      next: () => {
        this.showCreateDialog = false;
        this.saving.set(false);
        this.messageService.add({
          severity: 'success',
          summary: 'Success',
          detail: 'User created successfully'
        });
        this.loadUsers(0);
      },
      error: (err) => {
        this.saving.set(false);
        const e = err as { error?: { error?: string; message?: string } };
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: e?.error?.error || e?.error?.message || 'Failed to create user'
        });
      }
    });
  }

  toggleStatus(user: AppUser): void {
    const newStatus = user.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    const action = newStatus === 'ACTIVE' ? 'activate' : 'deactivate';

    this.confirmationService.confirm({
      message: `Are you sure you want to ${action} user <strong>${user.email}</strong>?`,
      header: 'Confirm Status Change',
      icon: 'pi pi-question-circle',
      accept: () => {
        this.userService.updateStatus(user.id, newStatus).subscribe({
          next: (updated) => {
            // Update the user in place
            this.users.update(list =>
              list.map(u => (u.id === updated.id ? updated : u))
            );
            this.messageService.add({
              severity: 'success',
              summary: 'Updated',
              detail: `User ${action}d successfully`
            });
          },
          error: (err) => {
            const e = err as { error?: { error?: string } };
            this.messageService.add({
              severity: 'error',
              summary: 'Error',
              detail: e?.error?.error || `Failed to ${action} user`
            });
          }
        });
      }
    });
  }

  confirmDelete(user: AppUser): void {
    this.confirmationService.confirm({
      message: `Are you sure you want to delete user <strong>${user.email}</strong>? This action cannot be undone.`,
      header: 'Confirm Delete',
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        this.userService.deleteUser(user.id).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: 'Deleted',
              detail: `User "${user.email}" deleted`
            });
            this.loadUsers(0);
          },
          error: (err) => {
            const e = err as { error?: { error?: string } };
            this.messageService.add({
              severity: 'error',
              summary: 'Error',
              detail: e?.error?.error || 'Failed to delete user'
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
      case 'SUSPENDED':
        return 'danger';
      default:
        return 'secondary';
    }
  }
}
