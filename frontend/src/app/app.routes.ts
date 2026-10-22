import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { AuthService } from './core/auth.service';
import { authGuard, guestGuard, roleGuard } from './core/guards';

const manage = roleGuard('ADMIN', 'API_MANAGER'), admin = roleGuard('ADMIN');
export const routes: Routes = [
  { path: 'login', canActivate: [guestGuard], title: 'Sign in · APIShield', loadComponent: () => import('./pages/login.page').then(m => m.LoginPage) },
  {
    path: '', canActivate: [authGuard], loadComponent: () => import('./layout/shell.component').then(m => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: () => inject(AuthService).homeRoute() },
      { path: 'dashboard', canActivate: [manage], title: 'Dashboard · APIShield', loadComponent: () => import('./pages/dashboard.page').then(m => m.DashboardPage) },
      { path: 'apis', canActivate: [manage], title: 'API registry · APIShield', loadComponent: () => import('./pages/apis.page').then(m => m.ApisPage) },
      { path: 'apis/new', canActivate: [manage], title: 'Register API · APIShield', loadComponent: () => import('./pages/api-form.page').then(m => m.ApiFormPage) },
      { path: 'apis/:id/edit', canActivate: [manage], title: 'Edit API · APIShield', loadComponent: () => import('./pages/api-form.page').then(m => m.ApiFormPage) },
      { path: 'apis/:id', canActivate: [manage], title: 'API details · APIShield', loadComponent: () => import('./pages/api-detail.page').then(m => m.ApiDetailPage) },
      { path: 'analytics', canActivate: [manage], title: 'Analytics · APIShield', loadComponent: () => import('./pages/analytics.page').then(m => m.AnalyticsPage) },
      { path: 'security', canActivate: [manage], title: 'Security center · APIShield', loadComponent: () => import('./pages/security.page').then(m => m.SecurityPage) },
      { path: 'keys', title: 'API key vault · APIShield', loadComponent: () => import('./pages/keys.page').then(m => m.KeysPage) },
      { path: 'portal', title: 'Developer portal · APIShield', loadComponent: () => import('./pages/portal.page').then(m => m.PortalPage) },
      { path: 'users', canActivate: [admin], title: 'Users · APIShield', loadComponent: () => import('./pages/users.page').then(m => m.UsersPage) },
      { path: 'audit', canActivate: [admin], title: 'Audit log · APIShield', loadComponent: () => import('./pages/audit.page').then(m => m.AuditPage) },
      { path: 'settings', title: 'Settings · APIShield', loadComponent: () => import('./pages/settings.page').then(m => m.SettingsPage) },
    ],
  },
  { path: 'forbidden', title: 'Access denied · APIShield', loadComponent: () => import('./pages/errors.page').then(m => m.ForbiddenPage) },
  { path: '**', title: 'Not found · APIShield', loadComponent: () => import('./pages/errors.page').then(m => m.NotFoundPage) },
];
