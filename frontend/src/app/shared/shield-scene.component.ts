import { AfterViewInit, Component, ElementRef, Input, NgZone, OnChanges, OnDestroy, SimpleChanges, ViewChild, inject } from '@angular/core';
import * as THREE from 'three';

/**
 * Signature 3D centrepiece: a translucent shield around a rotating core, with one satellite node per REAL registered API.
 * Request particles are emitted only at the rate of REAL traffic (requestsPerMinute); with no traffic nothing moves along the paths.
 * The scene is purely decorative — it never takes part in security decisions. Falls back to a static SVG when WebGL is unavailable.
 */
@Component({
  selector: 'app-shield-scene',
  template: `
    <div class="scene" #host [attr.role]="'img'" [attr.aria-label]="label">
      @if (failed) {
        <svg viewBox="0 0 200 200" class="fallback" aria-hidden="true">
          <path d="M100 14 L170 42 V104 C170 146 140 172 100 188 C60 172 30 146 30 104 V42 Z" fill="none" stroke="var(--teal)" stroke-width="3"/>
          <path d="M100 40 L146 58 V102 C146 130 126 148 100 160 C74 148 54 130 54 102 V58 Z" fill="rgba(200,241,105,.12)" stroke="var(--lime)" stroke-width="2"/>
          <circle cx="100" cy="98" r="14" fill="var(--coral)"/>
        </svg>
      }
    </div>`,
  styles: [`:host{display:block;width:100%;height:100%;min-height:240px}.scene{width:100%;height:100%;min-height:240px;position:relative}
    .scene ::ng-deep canvas{display:block;width:100%!important;height:100%!important}.fallback{width:60%;max-width:260px;margin:auto;display:block;padding-top:8%}`]
})
export class ShieldSceneComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input() nodes = 0;
  @Input() requestsPerMinute = 0;
  @Input() label = 'Three-dimensional shield visualisation of the API gateway';
  @ViewChild('host', { static: true }) host!: ElementRef<HTMLDivElement>;
  failed = false;

  private zone = inject(NgZone);
  private renderer?: THREE.WebGLRenderer; private scene = new THREE.Scene(); private camera = new THREE.PerspectiveCamera(42, 1, 0.1, 50);
  private group = new THREE.Group(); private nodeGroup = new THREE.Group(); private core?: THREE.Mesh; private ring1?: THREE.Mesh; private ring2?: THREE.Mesh;
  private particles: { mesh: THREE.Mesh; t: number; from: THREE.Vector3; via: THREE.Vector3; to: THREE.Vector3; active: boolean }[] = [];
  private nodePositions: THREE.Vector3[] = [];
  private raf = 0; private lastEmit = 0; private visible = true; private ro?: ResizeObserver; private io?: IntersectionObserver;
  private pointer = new THREE.Vector2(); private reduced = false; private clock = new THREE.Clock();
  private onMove = (e: PointerEvent) => { const r = this.host.nativeElement.getBoundingClientRect(); this.pointer.set(((e.clientX - r.left) / r.width) * 2 - 1, ((e.clientY - r.top) / r.height) * 2 - 1); };
  private onVis = () => { this.visible = !document.hidden; this.kick(); };

  ngAfterViewInit() {
    this.reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (!this.webglAvailable()) { this.failed = true; return; }
    try { this.zone.runOutsideAngular(() => this.init()); } catch { this.failed = true; this.dispose(); }
  }
  ngOnChanges(c: SimpleChanges) { if (c['nodes'] && this.renderer) { this.buildNodes(); this.renderOnce(); } }

  private webglAvailable() { try { const c = document.createElement('canvas'); return !!(c.getContext('webgl2') || c.getContext('webgl')); } catch { return false; } }

  private init() {
    const el = this.host.nativeElement;
    this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'low-power' });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.75));
    el.appendChild(this.renderer.domElement);
    this.camera.position.set(0, 0.6, 6.2);
    this.scene.add(new THREE.AmbientLight(0xF4F0E8, 0.6));
    const key = new THREE.DirectionalLight(0xF4F0E8, 1.4); key.position.set(3, 4, 5); this.scene.add(key);
    const rim = new THREE.PointLight(0x35D0BA, 18, 12); rim.position.set(-3, -1, 3); this.scene.add(rim);

    const shield = new THREE.Mesh(new THREE.IcosahedronGeometry(1.9, 1),
      new THREE.MeshPhysicalMaterial({ color: 0x35D0BA, transparent: true, opacity: 0.16, roughness: 0.15, metalness: 0.1, clearcoat: 1, side: THREE.DoubleSide, depthWrite: false }));
    const wire = new THREE.Mesh(new THREE.IcosahedronGeometry(1.92, 1), new THREE.MeshBasicMaterial({ color: 0xC8F169, wireframe: true, transparent: true, opacity: 0.32 }));
    this.core = new THREE.Mesh(new THREE.IcosahedronGeometry(0.62, 0), new THREE.MeshStandardMaterial({ color: 0xFF5C5C, emissive: 0xFF5C5C, emissiveIntensity: 0.35, roughness: 0.35, metalness: 0.3, flatShading: true }));
    const ringMat = (c: number) => new THREE.MeshBasicMaterial({ color: c, transparent: true, opacity: 0.75 });
    this.ring1 = new THREE.Mesh(new THREE.TorusGeometry(1.25, 0.012, 8, 96), ringMat(0xFFB547)); this.ring1.rotation.x = Math.PI / 2.4;
    this.ring2 = new THREE.Mesh(new THREE.TorusGeometry(1.45, 0.01, 8, 96), ringMat(0xF08AB4)); this.ring2.rotation.y = Math.PI / 3;
    this.group.add(shield, wire, this.core, this.ring1, this.ring2, this.nodeGroup);
    this.scene.add(this.group);

    const pGeo = new THREE.SphereGeometry(0.045, 8, 8), pMat = new THREE.MeshBasicMaterial({ color: 0xC8F169 });
    for (let i = 0; i < 28; i++) { const m = new THREE.Mesh(pGeo, pMat); m.visible = false; this.scene.add(m); this.particles.push({ mesh: m, t: 0, from: new THREE.Vector3(), via: new THREE.Vector3(), to: new THREE.Vector3(), active: false }); }
    this.buildNodes();

    this.ro = new ResizeObserver(() => { this.resize(); this.renderOnce(); }); this.ro.observe(el);
    this.io = new IntersectionObserver(es => { this.visible = es[0].isIntersecting && !document.hidden; this.kick(); }); this.io.observe(el);
    document.addEventListener('visibilitychange', this.onVis);
    if (!this.reduced) el.addEventListener('pointermove', this.onMove);
    this.resize(); this.renderOnce(); this.kick();
  }

  private buildNodes() {
    for (const c of [...this.nodeGroup.children]) { this.nodeGroup.remove(c); this.disposeObj(c); }
    this.nodePositions = [];
    const n = Math.min(this.nodes, 24);
    for (let i = 0; i < n; i++) {
      const a = (i / n) * Math.PI * 2, y = Math.sin(i * 1.7) * 0.9, r = 2.9;
      const p = new THREE.Vector3(Math.cos(a) * r, y, Math.sin(a) * r); this.nodePositions.push(p);
      const m = new THREE.Mesh(new THREE.SphereGeometry(0.11, 14, 14), new THREE.MeshStandardMaterial({ color: 0x35D0BA, emissive: 0x35D0BA, emissiveIntensity: 0.4 }));
      m.position.copy(p); this.nodeGroup.add(m);
      const line = new THREE.Line(new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(0, 0, 0), p]), new THREE.LineBasicMaterial({ color: 0x4169E1, transparent: true, opacity: 0.55 }));
      this.nodeGroup.add(line);
    }
  }

  private resize() {
    if (!this.renderer) return; const w = this.host.nativeElement.clientWidth || 300, h = this.host.nativeElement.clientHeight || 300;
    this.renderer.setSize(w, h, false); this.camera.aspect = w / h; this.camera.updateProjectionMatrix();
  }
  private renderOnce() { if (this.renderer) this.renderer.render(this.scene, this.camera); }
  private kick() { if (!this.reduced && this.visible && !this.raf && this.renderer) this.raf = requestAnimationFrame(this.tick); }

  private tick = () => {
    this.raf = 0; if (!this.visible || !this.renderer) return;
    const dt = Math.min(this.clock.getDelta(), 0.05), t = this.clock.elapsedTime;
    this.group.rotation.y += dt * 0.25;
    this.group.rotation.x += ((this.pointer.y * 0.25) - this.group.rotation.x) * 0.04;
    this.group.position.y = Math.sin(t * 0.9) * 0.06;
    if (this.core) { this.core.rotation.x += dt * 0.6; this.core.rotation.y += dt * 0.4; }
    if (this.ring1) this.ring1.rotation.z += dt * 0.5; if (this.ring2) this.ring2.rotation.x -= dt * 0.35;
    // emit particles only for real traffic
    const rpm = this.requestsPerMinute;
    if (rpm > 0 && this.nodePositions.length) {
      const interval = Math.max(0.15, Math.min(8, 60 / rpm));
      if (t - this.lastEmit > interval) { this.lastEmit = t; this.emit(); }
    }
    for (const p of this.particles) if (p.active) {
      p.t += dt * 0.45;
      if (p.t >= 1) { p.active = false; p.mesh.visible = false; continue; }
      const u = p.t < 0.5 ? p.t * 2 : (p.t - 0.5) * 2, a = p.t < 0.5 ? p.from : p.via, b = p.t < 0.5 ? p.via : p.to;
      p.mesh.position.lerpVectors(a, b, u);
    }
    this.renderer.render(this.scene, this.camera);
    this.raf = requestAnimationFrame(this.tick);
  };
  private emit() {
    const p = this.particles.find(x => !x.active); if (!p) return;
    const a = Math.random() * Math.PI * 2; p.from.set(Math.cos(a) * 5, (Math.random() - 0.5) * 3, Math.sin(a) * 5);
    p.via.set(0, 0, 0); p.to.copy(this.nodePositions[Math.floor(Math.random() * this.nodePositions.length)]);
    p.t = 0; p.active = true; p.mesh.visible = true; p.mesh.position.copy(p.from);
  }

  private disposeObj(o: THREE.Object3D) {
    o.traverse(c => { const m = c as THREE.Mesh; m.geometry?.dispose?.(); const mat = m.material as THREE.Material | THREE.Material[] | undefined; if (Array.isArray(mat)) mat.forEach(x => x.dispose()); else mat?.dispose?.(); });
  }
  private dispose() {
    cancelAnimationFrame(this.raf); this.raf = 0; this.ro?.disconnect(); this.io?.disconnect();
    document.removeEventListener('visibilitychange', this.onVis); this.host?.nativeElement.removeEventListener('pointermove', this.onMove);
    this.disposeObj(this.scene);
    if (this.renderer) { this.renderer.dispose(); this.renderer.forceContextLoss(); this.renderer.domElement.remove(); this.renderer = undefined; }
  }
  ngOnDestroy() { this.dispose(); }
}
