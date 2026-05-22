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

import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { ApiResponse, PageResult, Tenant, TenantMembership } from '../models/api.model';

@Injectable({ providedIn: 'root' })
export class TenantService {
  private base = `${environment.apiUrl}/api/backoffice/tenants`;

  constructor(private http: HttpClient) {}

  getTenants(page = 0, size = 20): Observable<PageResult<Tenant>> {
    return this.http
      .get<ApiResponse<PageResult<Tenant>>>(this.base, {
        params: new HttpParams().set('page', page).set('size', size)
      })
      .pipe(map(r => r.data));
  }

  getTenant(id: string): Observable<Tenant> {
    return this.http
      .get<ApiResponse<Tenant>>(`${this.base}/${id}`)
      .pipe(map(r => r.data));
  }

  createTenant(name: string, clusterId: string): Observable<Tenant> {
    return this.http
      .post<ApiResponse<Tenant>>(this.base, { name, clusterId })
      .pipe(map(r => r.data));
  }

  updateStatus(id: string, status: string): Observable<Tenant> {
    return this.http
      .put<ApiResponse<Tenant>>(`${this.base}/${id}/status`, null, {
        params: new HttpParams().set('status', status)
      })
      .pipe(map(r => r.data));
  }

  assignCluster(id: string, clusterId: string): Observable<Tenant> {
    return this.http
      .put<ApiResponse<Tenant>>(`${this.base}/${id}/cluster`, null, {
        params: new HttpParams().set('clusterId', clusterId)
      })
      .pipe(map(r => r.data));
  }

  deleteTenant(id: string): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`${this.base}/${id}`)
      .pipe(map(() => undefined));
  }

  getTenantUsers(tenantId: string): Observable<TenantMembership[]> {
    return this.http
      .get<ApiResponse<TenantMembership[]>>(`${this.base}/${tenantId}/users`)
      .pipe(map(r => r.data));
  }

  assignUser(tenantId: string, userId: string, role: string): Observable<void> {
    return this.http
      .post<ApiResponse<void>>(`${this.base}/${tenantId}/users`, { userId, role })
      .pipe(map(() => undefined));
  }

  removeUser(tenantId: string, userId: string): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`${this.base}/${tenantId}/users/${userId}`)
      .pipe(map(() => undefined));
  }

  provisionTenant(tenantId: string): Observable<void> {
    return this.http
      .post<ApiResponse<void>>(`${this.base}/${tenantId}/provision`, null)
      .pipe(map(() => undefined));
  }

  upgradeTenant(tenantId: string): Observable<void> {
    return this.http
      .post<ApiResponse<void>>(`${this.base}/${tenantId}/upgrade`, null)
      .pipe(map(() => undefined));
  }
}
