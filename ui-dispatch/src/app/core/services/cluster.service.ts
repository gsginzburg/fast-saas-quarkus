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
import { ApiResponse, Cluster, PageResult } from '../models/api.model';

@Injectable({ providedIn: 'root' })
export class ClusterService {
  private base = `${environment.apiUrl}/api/backoffice/clusters`;

  constructor(private http: HttpClient) {}

  getAllClusters(): Observable<Cluster[]> {
    return this.http
      .get<ApiResponse<Cluster[]>>(`${this.base}/all`)
      .pipe(map(r => r.data));
  }

  getClusters(page = 0, size = 20): Observable<PageResult<Cluster>> {
    return this.http
      .get<ApiResponse<PageResult<Cluster>>>(this.base, {
        params: new HttpParams().set('page', page).set('size', size)
      })
      .pipe(map(r => r.data));
  }

  createCluster(name: string, url: string, apiUrl: string): Observable<Cluster> {
    return this.http
      .post<ApiResponse<Cluster>>(this.base, { name, url, apiUrl })
      .pipe(map(r => r.data));
  }

  updateCluster(id: string, name: string, url: string, apiUrl: string): Observable<Cluster> {
    return this.http
      .put<ApiResponse<Cluster>>(`${this.base}/${id}`, { name, url, apiUrl })
      .pipe(map(r => r.data));
  }

  deleteCluster(id: string): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`${this.base}/${id}`)
      .pipe(map(() => undefined));
  }

  upgradeAll(id: string): Observable<void> {
    return this.http
      .post<ApiResponse<void>>(`${this.base}/${id}/upgrade-all`, null)
      .pipe(map(() => undefined));
  }
}
