import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({ selector: 'app-forbidden', imports: [RouterLink], template: `<main class="center"><div class="panel empty"><h1>Access denied</h1><p>Your role does not allow you to view this page. The server enforces this independently of the interface.</p><a class="btn primary" [routerLink]="auth.isAuthenticated() ? auth.homeRoute() : '/login'">Go back</a></div></main>`, styles: [`.center{min-height:100vh;display:grid;place-items:center;padding:24px}`] })
export class ForbiddenPage { auth = inject(AuthService); }

@Component({ selector: 'app-not-found', imports: [RouterLink], template: `<main class="center"><div class="panel empty"><h1>Page not found</h1><p>The page you are looking for does not exist.</p><a class="btn primary" [routerLink]="auth.isAuthenticated() ? auth.homeRoute() : '/login'">Take me home</a></div></main>`, styles: [`.center{min-height:100vh;display:grid;place-items:center;padding:24px}`] })
export class NotFoundPage { auth = inject(AuthService); }
