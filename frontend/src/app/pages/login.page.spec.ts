import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { provideRouter } from '@angular/router';
import { LoginPage } from './login.page';

@Component({ selector: 'app-shield-scene', template: '' }) class SceneStub {}

describe('LoginPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [LoginPage], providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    TestBed.overrideComponent(LoginPage, { remove: { imports: [(require('../shared/shield-scene.component') as any).ShieldSceneComponent] }, add: { imports: [SceneStub] } });
  });
  const mk = () => { const f = TestBed.createComponent(LoginPage); f.detectChanges(); return f; };

  it('shows inline validation and does not call the API for an invalid form', () => {
    const f = mk(); (f.nativeElement.querySelector('button[type=submit]') as HTMLButtonElement).click(); f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Enter a valid email address.');
    TestBed.inject(HttpTestingController).expectNone('/api/v1/auth/login');
  });
  it('enforces the 10 character password rule when registering', () => {
    const f = mk(); const c = f.componentInstance; c.switch('register'); c.form.setValue({ displayName: 'A', email: 'a@b.co', password: 'short' }); c.submit(); f.detectChanges();
    expect(f.nativeElement.textContent).toContain('at least 10 characters'); TestBed.inject(HttpTestingController).expectNone('/api/v1/auth/register');
  });
  it('displays the server error without leaking details', () => {
    const f = mk(); const c = f.componentInstance; c.form.setValue({ displayName: '', email: 'a@b.co', password: 'whatever-pass' }); c.submit();
    TestBed.inject(HttpTestingController).expectOne('/api/v1/auth/login').flush({ code: 'INVALID_CREDENTIALS', message: 'Invalid email or password' }, { status: 401, statusText: 'Unauthorized' });
    f.detectChanges(); expect(f.nativeElement.querySelector('[role=alert]').textContent).toContain('Invalid email or password');
  });
});
