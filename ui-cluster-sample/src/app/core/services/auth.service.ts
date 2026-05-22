import { Injectable, signal, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, of } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly TOKEN_KEY = 'cluster_token';
  private readonly CLUSTER_URL_KEY = 'cluster_url';

  private router = inject(Router);

  private _isLoggedIn = signal(!!localStorage.getItem(this.TOKEN_KEY));
  isLoggedIn = computed(() => this._isLoggedIn());

  initFromToken(token: string): Observable<void> {
    localStorage.setItem(this.TOKEN_KEY, token);
    localStorage.setItem(this.CLUSTER_URL_KEY, environment.clusterUrl);
    this._isLoggedIn.set(true);
    return of(undefined);
  }

  logout(): void {
    localStorage.clear();
    this._isLoggedIn.set(false);
    this.router.navigate(['/unauthorized']);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  getClusterUrl(): string {
    return localStorage.getItem(this.CLUSTER_URL_KEY) || environment.clusterUrl;
  }
}
