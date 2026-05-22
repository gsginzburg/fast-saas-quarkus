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

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  error?: string;
  message?: string;
}

export interface PageResult<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  pageNumber: number;
  pageSize: number;
}

export interface Tenant {
  id: string;
  name: string;
  status: 'ACTIVE' | 'INACTIVE' | 'ARCHIVED';
  clusterId: string;
  clusterName: string;
  clusterUrl: string;
  deepLinkUrl: string;
  createdAt: string;
  updatedAt: string;
}

export interface Cluster {
  id: string;
  name: string;
  url: string;
  apiUrl: string;
  status: 'ACTIVE' | 'INACTIVE';
  createdAt: string;
}

export interface AppUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  userType: 'BACKOFFICE' | 'TENANT';
  status: 'ACTIVE' | 'INACTIVE' | 'SUSPENDED';
  createdAt: string;
  tenantMemberships?: TenantMembership[];
}

export interface TenantMembership {
  userId: string;
  userEmail?: string;
  tenantId: string;
  tenantName: string;
  role: string;
  status: string;
}
