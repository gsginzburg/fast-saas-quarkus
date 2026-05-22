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

import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap, map } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LoginRequest, LoginResponse } from '../models/auth.model';
import { ApiResponse } from '../models/api.model';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly ACCESS_TOKEN_KEY = 'dispatch_access_token';
  private readonly REFRESH_TOKEN_KEY = 'dispatch_refresh_token';
  private readonly USER_KEY = 'dispatch_user';

  private _isLoggedIn = signal(!!localStorage.getItem(this.ACCESS_TOKEN_KEY));
  isLoggedIn = computed(() => this._isLoggedIn());

  constructor(private http: HttpClient, private router: Router) {}

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<ApiResponse<LoginResponse>>(`${environment.apiUrl}/api/auth/login`, request)
      .pipe(
        map(r => r.data),
        tap(data => {
          localStorage.setItem(this.ACCESS_TOKEN_KEY, data.accessToken);
          localStorage.setItem(this.REFRESH_TOKEN_KEY, data.refreshToken);
          localStorage.setItem(
            this.USER_KEY,
            JSON.stringify({ email: data.email, userId: data.userId })
          );
          this._isLoggedIn.set(true);
        })
      );
  }

  logout(): void {
    this.http.post(`${environment.apiUrl}/api/auth/logout`, {}).subscribe({
      complete: () => this.clearAndRedirect(),
      error: () => this.clearAndRedirect()
    });
  }

  private clearAndRedirect(): void {
    localStorage.removeItem(this.ACCESS_TOKEN_KEY);
    localStorage.removeItem(this.REFRESH_TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    this._isLoggedIn.set(false);
    this.router.navigate(['/login']);
  }

  scopeToTenant(tenantId: string): Observable<string> {
    return this.http
      .post<ApiResponse<{ scopedToken: string }>>(`${environment.apiUrl}/api/auth/scope-to-tenant`, { tenantId })
      .pipe(map(r => r.data.scopedToken));
  }

  getToken(): string | null {
    return localStorage.getItem(this.ACCESS_TOKEN_KEY);
  }

  getCurrentUser(): { email: string; userId: string } | null {
    const stored = localStorage.getItem(this.USER_KEY);
    return stored ? JSON.parse(stored) : null;
  }
}
