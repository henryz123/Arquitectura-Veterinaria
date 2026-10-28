import { DatePipe, NgFor, NgIf } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { GroomingTask } from '../../core/api/api.models';
import { ScheduleApiService } from '../../core/api/schedule-api.service';
import { AuthStateService } from '../../core/auth/auth-state.service';

@Component({
  selector: 'app-grooming-page',
  imports: [DatePipe, NgFor, NgIf, ReactiveFormsModule, RouterLink],
  templateUrl: './grooming-page.component.html',
  styleUrl: './grooming-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GroomingPageComponent implements OnInit {
  private readonly scheduleApi = inject(ScheduleApiService);
  private readonly auth = inject(AuthStateService);
  private readonly formBuilder = inject(FormBuilder);

  readonly context = this.auth.context;
  readonly canOperate = computed(() => this.context()?.accountType === 'STAFF' && this.context()?.roles.includes('GROOMING'));
  readonly tasks = signal<GroomingTask[]>([]);
  readonly selectedTask = signal<GroomingTask | null>(null);
  readonly loading = signal(true);
  readonly actionPending = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly completionSubmitted = signal(false);
  readonly pendingCount = computed(() => this.tasks().filter((task) => task.reservationStatus === 'CONFIRMADA').length);
  readonly inProgressCount = computed(() => this.tasks().filter((task) => task.reservationStatus === 'EN_ATENCION').length);
  readonly completedCount = computed(() => this.tasks().filter((task) => task.reservationStatus === 'COMPLETADA').length);
  readonly completionForm = this.formBuilder.nonNullable.group({
    result: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(1000)]],
    operationalNotes: ['', Validators.maxLength(2000)],
  });

  ngOnInit(): void {
    if (!this.canOperate()) {
      this.loading.set(false);
      return;
    }
    this.loadTasks();
  }

  loadTasks(): void {
    if (!this.canOperate()) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.scheduleApi.listGroomingTasks().pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (response) => this.tasks.set(response.data),
      error: () => this.error.set('No fue posible cargar tus tareas asignadas. Actualiza la pantalla para revisar el estado vigente.'),
    });
  }

  startTask(task: GroomingTask): void {
    if (task.reservationStatus !== 'CONFIRMADA' || this.actionPending() || !this.canOperate()) {
      return;
    }
    this.clearFeedback();
    this.actionPending.set(true);
    this.scheduleApi.startGroomingAttention(task.reservationId).pipe(finalize(() => this.actionPending.set(false))).subscribe({
      next: () => {
        this.notice.set('El servicio fue iniciado. Solo tú, como encargado asignado, puedes registrar su resultado operativo.');
        this.loadTasks();
      },
      error: () => this.error.set('No fue posible iniciar el servicio. Confirma que sigue asignado a tu cuenta y que la reserva permanece confirmada.'),
    });
  }

  selectForCompletion(task: GroomingTask): void {
    if (task.reservationStatus !== 'EN_ATENCION' || this.actionPending()) {
      return;
    }
    this.selectedTask.set(task);
    this.completionSubmitted.set(false);
    this.completionForm.reset({ result: '', operationalNotes: '' });
    this.clearFeedback();
  }

  cancelCompletion(): void {
    if (this.actionPending()) {
      return;
    }
    this.selectedTask.set(null);
    this.completionSubmitted.set(false);
    this.completionForm.reset({ result: '', operationalNotes: '' });
  }

  completeTask(): void {
    const task = this.selectedTask();
    this.completionSubmitted.set(true);
    if (!task || task.reservationStatus !== 'EN_ATENCION' || this.completionForm.invalid || this.actionPending() || !this.canOperate()) {
      this.completionForm.markAllAsTouched();
      return;
    }
    const value = this.completionForm.getRawValue();
    this.clearFeedback();
    this.actionPending.set(true);
    this.scheduleApi.completeGroomingAttention(task.reservationId, {
      result: value.result.trim(),
      operationalNotes: value.operationalNotes.trim() || null,
    }).pipe(finalize(() => this.actionPending.set(false))).subscribe({
      next: () => {
        this.cancelCompletion();
        this.notice.set('El servicio quedó completado y su resultado operativo fue conservado en el historial de la reserva.');
        this.loadTasks();
      },
      error: () => this.error.set('No fue posible completar el servicio. Actualiza la lista: la reserva o la asignación pudieron cambiar.'),
    });
  }

  trackByReservation(_: number, task: GroomingTask): string {
    return task.reservationId;
  }

  private clearFeedback(): void {
    this.error.set(null);
    this.notice.set(null);
  }
}
