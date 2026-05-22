import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { ApiResponse, FullContext, TestRecord } from '../models/api.model';

@Injectable({ providedIn: 'root' })
export class ClusterApiService {
  private http = inject(HttpClient);

  private get base(): string {
    return environment.clusterUrl;
  }

  getContext(): Observable<FullContext> {
    return this.http
      .get<ApiResponse<FullContext>>(`${this.base}/api/app/context`)
      .pipe(map(r => r.data));
  }

  getTestRecords(): Observable<TestRecord[]> {
    return this.http
      .get<ApiResponse<TestRecord[]>>(`${this.base}/api/app/test-records`)
      .pipe(map(r => r.data));
  }

  createTestRecord(data: { name: string; description?: string; value?: number }): Observable<TestRecord> {
    return this.http
      .post<ApiResponse<TestRecord>>(`${this.base}/api/app/test-records`, data)
      .pipe(map(r => r.data));
  }

  deleteTestRecord(id: string): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`${this.base}/api/app/test-records/${id}`)
      .pipe(map(() => undefined));
  }
}
