export interface ApiResponse<T> {
  success: boolean;
  data: T;
  error?: string;
}

export interface TenantInfo {
  id: string;
  name: string;
  status: string;
  clusterId: string;
  clusterName: string;
  clusterUrl: string;
}

export interface UserInfo {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  userType: string;
  roles: string[];
}

export interface ClusterInfo {
  id: string;
  name: string;
  url: string;
  status: string;
}

export interface LocalContext {
  userId: string;
  schemaName: string;
}

export interface FullContext {
  tenant: TenantInfo;
  user: UserInfo;
  cluster: ClusterInfo;
  localContext: LocalContext;
}

export interface TestRecord {
  id: string;
  name: string;
  description?: string;
  value?: number;
  createdAt: string;
}
