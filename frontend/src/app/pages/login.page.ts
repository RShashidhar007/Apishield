import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { errorMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ShieldSceneComponent } from '../shared/shield-scene.component';

@Component({
  selector: 'app-login', imports: [ReactiveFormsModule, ShieldSceneComponent],
  template: `
  <div class="split">
    <section class="left theme-light">
      <div class="inner">
        <p class="eyebrow">APIShield</p>
        <h1>Secure every request.<br><em>Control every API.</em></h1>
        <p class="lead">Gateway, rate limits, keys and threat signals — one command centre.</p>

        <div class="tabs" role="tablist"><button role="tab" [attr.aria-selected]="mode() === 'login'" (click)="switch('login')">Sign in</button><button role="tab" [attr.aria-selected]="mode() === 'register'" (click)="switch('register')">Create account</button></div>

        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          @if (error()) { <div class="banner bad" role="alert">{{ error() }}</div> }
          @if (mode() === 'register') {
            <div class="field" [class.invalid]="bad('displayName')"><label for="dn">Display name</label><input id="dn" type="text" formControlName="displayName" autocomplete="name" [attr.aria-invalid]="bad('displayName')">
              @if (bad('displayName')) { <span class="error-text">Enter your name.</span> }</div>
          }
          <div class="field" [class.invalid]="bad('email')"><label for="em">Email</label><input id="em" type="email" formControlName="email" autocomplete="email" [attr.aria-invalid]="bad('email')">
            @if (bad('email')) { <span class="error-text">Enter a valid email address.</span> }</div>
          <div class="field" [class.invalid]="bad('password')"><label for="pw">Password</label><input id="pw" type="password" formControlName="password" [autocomplete]="mode() === 'login' ? 'current-password' : 'new-password'" [attr.aria-invalid]="bad('password')">
            @if (bad('password')) { <span class="error-text">{{ mode() === 'register' ? 'Use at least 10 characters.' : 'Enter your password.' }}</span> }
            @if (mode() === 'register') { <span class="hint">10–72 characters. New accounts start as developers.</span> }</div>
          <button class="btn primary" type="submit" [disabled]="busy()">@if (busy()) { <span class="spin" aria-hidden="true"></span> Working… } @else { {{ mode() === 'login' ? 'Sign in' : 'Create account' }} }</button>
        </form>
      </div>
    </section>
    <section class="right" aria-hidden="false"><app-shield-scene [nodes]="0" [requestsPerMinute]="0" label="Decorative 3D shield illustration"/>
      <p class="cap">Every policy on this platform is enforced by the gateway and backend — not the interface.</p></section>
  </div>`,
  styles: [`
    .split{display:grid;grid-template-columns:minmax(380px,5fr) 6fr;min-height:100vh}
    .left{background:var(--ivory);color:var(--text);display:grid;align-items:center;padding:clamp(24px,5vw,72px)}.inner{max-width:460px;width:100%}
    h1 em{color:#0f8f7e;font-style:italic}.lead{color:var(--muted);font-size:1.05rem;margin-bottom:var(--s5)}
    .tabs{display:flex;gap:4px;margin-bottom:var(--s5);border-bottom:2px solid var(--line)}
    .tabs button{background:none;border:0;padding:10px 14px;font:inherit;font-weight:700;color:var(--muted);cursor:pointer;border-bottom:3px solid transparent;margin-bottom:-2px}
    .tabs button[aria-selected=true]{color:var(--text);border-bottom-color:var(--coral)}
    .right{background:radial-gradient(circle at 60% 40%,#1e2330,var(--obsidian) 70%);display:grid;grid-template-rows:1fr auto;padding:32px;color:var(--ivory)}.cap{text-align:center;color:#a6a39a;max-width:420px;margin:0 auto}
    .spin{width:14px;height:14px;border:2px solid #14170a;border-right-color:transparent;border-radius:50%;animation:s .7s linear infinite;display:inline-block}@keyframes s{to{transform:rotate(360deg)}}
    @media (max-width:860px){.split{grid-template-columns:1fr}.right{display:none}}`]
})
export class LoginPage {
  private fb = inject(FormBuilder); private auth = inject(AuthService); private router = inject(Router); private route = inject(ActivatedRoute);
  mode = signal<'login' | 'register'>('login'); busy = signal(false); error = signal('');
  form = this.fb.nonNullable.group({ displayName: [''], email: ['', [Validators.required, Validators.email]], password: ['', [Validators.required]] });

  switch(m: 'login' | 'register') {
    this.mode.set(m); this.error.set('');
    const dn = this.form.controls.displayName, pw = this.form.controls.password;
    dn.setValidators(m === 'register' ? [Validators.required, Validators.maxLength(100)] : []);
    pw.setValidators(m === 'register' ? [Validators.required, Validators.minLength(10), Validators.maxLength(72)] : [Validators.required]);
    dn.updateValueAndValidity(); pw.updateValueAndValidity(); this.form.markAsUntouched();
  }
  bad(c: 'displayName' | 'email' | 'password') { const x = this.form.controls[c]; return x.invalid && (x.touched || x.dirty); }

  submit() {
    this.form.markAllAsTouched(); if (this.form.invalid || this.busy()) return;
    const { email, password, displayName } = this.form.getRawValue(); this.busy.set(true); this.error.set('');
    const done = () => this.router.navigateByUrl(this.safeNext() ?? this.auth.homeRoute());
    const fail = (e: unknown) => { this.busy.set(false); this.error.set(errorMessage(e)); };
    if (this.mode() === 'login') this.auth.login(email, password).subscribe({ next: done, error: fail });
    else this.auth.register(email, displayName, password).subscribe({ next: () => this.auth.login(email, password).subscribe({ next: done, error: fail }), error: fail });
  }
  /** Only same-origin relative paths are honoured (prevents open redirects). */
  private safeNext() { const n = this.route.snapshot.queryParamMap.get('next'); return n && n.startsWith('/') && !n.startsWith('//') ? n : null; }
}
