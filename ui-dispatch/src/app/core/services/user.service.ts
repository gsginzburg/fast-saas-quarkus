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
import { ApiResponse, AppUser, PageResult } from '../models/api.model';

export interface CreateUserRequest {
  email: string;
  password: string;
  firstName?: string;
  lastName?: string;
  userType: string;
}

@Injectable({ providedIn: 'root' })
export class UserService {
  private base = `${environment.apiUrl}/api/backoffice/users`;

  constructor(private http: HttpClient) {}

  getUsers(page = 0, size = 20): Observable<PageResult<AppUser>> {
    return this.http
      .get<ApiResponse<PageResult<AppUser>>>(this.base, {
        params: new HttpParams().set('page', page).set('size', size)
      })
      .pipe(map(r => r.data));
  }

  getUser(id: string): Observable<AppUser> {
    return this.http
      .get<ApiResponse<AppUser>>(`${this.base}/${id}`)
      .pipe(map(r => r.data));
  }

  createUser(data: CreateUserRequest): Observable<AppUser> {
    return this.http
      .post<ApiResponse<AppUser>>(this.base, data)
      .pipe(map(r => r.data));
  }

  deleteUser(id: string): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`${this.base}/${id}`)
      .pipe(map(() => undefined));
  }

  updateStatus(id: string, status: string): Observable<AppUser> {
    return this.http
      .put<ApiResponse<AppUser>>(`${this.base}/${id}/status`, null, {
        params: new HttpParams().set('status', status)
      })
      .pipe(map(r => r.data));
  }
}
