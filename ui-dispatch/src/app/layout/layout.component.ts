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

import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MenubarModule } from 'primeng/menubar';
import { ButtonModule } from 'primeng/button';
import { MenuItem } from 'primeng/api';
import { AuthService } from '../core/services/auth.service';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterOutlet, MenubarModule, ButtonModule],
  templateUrl: './layout.component.html'
})
export class LayoutComponent {
  private auth = inject(AuthService);
  currentUser = this.auth.getCurrentUser();

  menuItems: MenuItem[] = [
    { label: 'Tenants', icon: 'pi pi-building', routerLink: '/tenants' },
    { label: 'Users', icon: 'pi pi-users', routerLink: '/users' },
    { label: 'Clusters', icon: 'pi pi-server', routerLink: '/clusters' }
  ];

  logout(): void {
    this.auth.logout();
  }
}
