import { Component, ElementRef, OnDestroy, effect, input, output, signal, untracked, viewChild } from '@angular/core';

/**
 * Frames a photo before it uploads: fit the whole photo (white margins) or fill the frame with zoom and drag, and
 * rotate. Listing photos are 4:3 like the catalogue card; profile photos are square. Emits a new JPEG, the original
 * file (when allowed), or nothing.
 */
@Component({
  selector: 'app-photo-editor',
  standalone: true,
  template: `
    <div class="dialog-backdrop">
      <div class="dialog" role="dialog" aria-modal="true" aria-label="Edit photo" style="max-width: 640px; width: 100%;">
        <h2>Edit photo</h2>
        <p class="muted" style="font-size: .875rem;">{{ file().name }}@if (position()) { · photo {{ position() }} }. {{ caption() }}</p>

        <canvas #preview width="640" [height]="640 * outputHeight() / outputWidth()" aria-label="Photo preview"
                [style.aspect-ratio]="outputWidth() + ' / ' + outputHeight()" [style.border-radius]="round() ? '50%' : null"
                [style.width]="round() ? 'min(100%, 360px)' : null" [style.margin-inline]="round() ? 'auto' : null"
                [style.cursor]="mode() === 'fill' ? (dragging ? 'grabbing' : 'grab') : 'default'"
                style="display: block; width: 100%; margin-top: .75rem; border: 1px solid var(--color-border); border-radius: var(--radius-md); touch-action: none;"
                (pointerdown)="startDrag($event)" (pointermove)="drag($event)" (pointerup)="endDrag($event)" (pointercancel)="endDrag($event)"></canvas>

        <div style="display: flex; flex-wrap: wrap; gap: 1rem; align-items: center; margin-top: .85rem;">
          <div role="radiogroup" aria-label="Framing" style="display: flex; gap: .75rem;">
            <label><input type="radio" name="photo-mode" [checked]="mode() === 'fit'" (change)="setMode('fit')" /> Fit whole photo</label>
            <label><input type="radio" name="photo-mode" [checked]="mode() === 'fill'" (change)="setMode('fill')" /> Fill the frame</label>
          </div>
          <div style="display: flex; gap: .35rem;">
            <button type="button" class="btn btn-secondary" style="padding: .35rem .65rem;" (click)="rotate(-90)" aria-label="Rotate left">↺ Rotate</button>
            <button type="button" class="btn btn-secondary" style="padding: .35rem .65rem;" (click)="rotate(90)" aria-label="Rotate right">↻</button>
          </div>
        </div>
        @if (mode() === 'fill') {
          <label style="display: flex; gap: .75rem; align-items: center; margin-top: .75rem;">Zoom
            <input type="range" min="1" max="3" step="0.05" [value]="zoom()" (input)="setZoom($event)" style="flex: 1;" />
          </label>
          <p class="muted" style="font-size: .8125rem; margin-top: .25rem;">Drag the photo to choose which part shows.</p>
        }
        @if (loadError()) {
          <div class="alert alert-error" role="alert">This photo could not be opened for editing. Use the original, or skip it.</div>
        }

        <div class="dialog-actions" style="flex-wrap: wrap;">
          <button type="button" class="btn btn-secondary" (click)="cancelled.emit()">{{ cancelLabel() }}</button>
          @if (allowOriginal()) {
            <button type="button" class="btn btn-secondary" (click)="saved.emit(file())">Use original</button>
          }
          <button type="button" class="btn btn-primary" (click)="save()" [disabled]="!image() || saving()">
            {{ saving() ? 'Saving…' : 'Save photo' }}
          </button>
        </div>
      </div>
    </div>
  `
})
export class PhotoEditorComponent implements OnDestroy {
  readonly file = input.required<File>();
  /** e.g. "2 of 3" when several photos are edited in turn. */
  readonly position = input<string>('');
  /** The saved size; the preview has the same shape. Listing photos match the 4:3 catalogue card. */
  readonly outputWidth = input(1200);
  readonly outputHeight = input(900);
  readonly caption = input('This is how it will look on the catalogue card.');
  readonly startMode = input<'fit' | 'fill'>('fit');
  /** Shows the preview as a circle, the way a profile photo is displayed. */
  readonly round = input(false);
  readonly allowOriginal = input(true);
  readonly cancelLabel = input('Skip this photo');
  readonly saved = output<File>();
  readonly cancelled = output<void>();

  readonly mode = signal<'fit' | 'fill'>('fit');
  readonly zoom = signal(1);
  readonly rotation = signal(0);
  readonly image = signal<HTMLImageElement | null>(null);
  readonly loadError = signal(false);
  readonly saving = signal(false);

  /** Optional, not required: the drawing effect can run before the view is ready. */
  private readonly canvas = viewChild<ElementRef<HTMLCanvasElement>>('preview');
  /** Offset of the photo's centre from the frame's centre, as fractions of the frame's width and height. */
  private offset = { x: 0, y: 0 };
  private objectUrl: string | null = null;
  dragging = false;
  private dragStart = { x: 0, y: 0, offsetX: 0, offsetY: 0 };

  constructor() {
    // A new file (the next photo in a batch) starts from a clean slate.
    effect(() => this.open(this.file()));
    effect(() => {
      this.mode(); this.zoom(); this.rotation(); this.image(); this.canvas();
      this.render();
    });
  }

  ngOnDestroy(): void {
    if (this.objectUrl) {
      URL.revokeObjectURL(this.objectUrl);
    }
  }

  setMode(mode: 'fit' | 'fill'): void {
    this.mode.set(mode);
    this.offset = { x: 0, y: 0 };
    this.zoom.set(1);
  }

  setZoom(event: Event): void {
    this.zoom.set(Number((event.target as HTMLInputElement).value));
    this.clampOffset();
  }

  rotate(degrees: number): void {
    this.rotation.update(current => (current + degrees + 360) % 360);
    this.offset = { x: 0, y: 0 };
  }

  startDrag(event: PointerEvent): void {
    if (this.mode() !== 'fill') {
      return;
    }
    this.dragging = true;
    this.dragStart = { x: event.clientX, y: event.clientY, offsetX: this.offset.x, offsetY: this.offset.y };
    (event.target as HTMLElement).setPointerCapture(event.pointerId);
  }

  drag(event: PointerEvent): void {
    if (!this.dragging) {
      return;
    }
    const box = this.canvas()!.nativeElement.getBoundingClientRect();
    this.offset = {
      x: this.dragStart.offsetX + (event.clientX - this.dragStart.x) / box.width,
      y: this.dragStart.offsetY + (event.clientY - this.dragStart.y) / box.height
    };
    this.clampOffset();
    this.render();
  }

  endDrag(event: PointerEvent): void {
    this.dragging = false;
    (event.target as HTMLElement).releasePointerCapture?.(event.pointerId);
  }

  save(): void {
    const image = this.image();
    if (!image) {
      return;
    }
    this.saving.set(true);
    const canvas = document.createElement('canvas');
    canvas.width = this.outputWidth();
    canvas.height = this.outputHeight();
    this.draw(canvas, image);
    canvas.toBlob(blob => {
      this.saving.set(false);
      if (!blob) {
        this.loadError.set(true);
        return;
      }
      const name = this.file().name.replace(/\.[^.]+$/, '') + '.jpg';
      this.saved.emit(new File([blob], name, { type: 'image/jpeg' }));
    }, 'image/jpeg', 0.9);
  }

  private open(file: File): void {
    if (this.objectUrl) {
      URL.revokeObjectURL(this.objectUrl);
    }
    this.image.set(null);
    this.loadError.set(false);
    this.mode.set(untracked(this.startMode));
    this.zoom.set(1);
    this.rotation.set(0);
    this.offset = { x: 0, y: 0 };
    this.objectUrl = URL.createObjectURL(file);
    const image = new Image();
    image.onload = () => this.image.set(image);
    image.onerror = () => this.loadError.set(true);
    image.src = this.objectUrl;
  }

  private render(): void {
    const image = this.image();
    const canvas = this.canvas()?.nativeElement;
    if (canvas && image) {
      this.draw(canvas, image);
    }
  }

  /** The photo's size, in frame pixels, after rotation and the current framing. */
  private scaled(frameWidth: number, frameHeight: number, image: HTMLImageElement) {
    const sideways = this.rotation() % 180 !== 0;
    const width = sideways ? image.naturalHeight : image.naturalWidth;
    const height = sideways ? image.naturalWidth : image.naturalHeight;
    const scale = this.mode() === 'fit'
      ? Math.min(frameWidth / width, frameHeight / height)
      : Math.max(frameWidth / width, frameHeight / height) * this.zoom();
    return { scale, width: width * scale, height: height * scale };
  }

  /** In fill mode the photo must always cover the frame, so it cannot be dragged past its edges. */
  private clampOffset(): void {
    const image = this.image();
    if (!image || this.mode() !== 'fill') {
      this.offset = { x: 0, y: 0 };
      return;
    }
    const frameWidth = this.outputWidth();
    const frameHeight = this.outputHeight();
    const { width, height } = this.scaled(frameWidth, frameHeight, image);
    const maxX = Math.max(0, (width - frameWidth) / 2 / frameWidth);
    const maxY = Math.max(0, (height - frameHeight) / 2 / frameHeight);
    this.offset = {
      x: Math.min(maxX, Math.max(-maxX, this.offset.x)),
      y: Math.min(maxY, Math.max(-maxY, this.offset.y))
    };
  }

  private draw(canvas: HTMLCanvasElement, image: HTMLImageElement): void {
    const context = canvas.getContext('2d')!;
    const { scale } = this.scaled(canvas.width, canvas.height, image);
    context.setTransform(1, 0, 0, 1, 0, 0);
    context.fillStyle = '#ffffff';
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.imageSmoothingQuality = 'high';
    context.translate(canvas.width / 2 + this.offset.x * canvas.width, canvas.height / 2 + this.offset.y * canvas.height);
    context.rotate(this.rotation() * Math.PI / 180);
    const width = image.naturalWidth * scale;
    const height = image.naturalHeight * scale;
    context.drawImage(image, -width / 2, -height / 2, width, height);
  }
}
