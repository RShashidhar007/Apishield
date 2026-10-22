import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ConfirmHostComponent, ToastHostComponent } from './shared/ui';

@Component({
  selector: 'app-root', imports: [RouterOutlet, ToastHostComponent, ConfirmHostComponent],
  template: `<router-outlet /><app-toast-host /><app-confirm-host />`
})
export class AppComponent {}
