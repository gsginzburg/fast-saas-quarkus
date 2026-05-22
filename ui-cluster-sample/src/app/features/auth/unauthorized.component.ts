import { Component } from '@angular/core';
import { CardModule } from 'primeng/card';

@Component({
  selector: 'app-unauthorized',
  standalone: true,
  imports: [CardModule],
  templateUrl: './unauthorized.component.html'
})
export class UnauthorizedComponent {}
