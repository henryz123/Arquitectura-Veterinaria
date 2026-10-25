import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiEnvelope, AssignableProfessional } from './api.models';

@Injectable({ providedIn: 'root' })
export class ScheduleApiService {
  private readonly http = inject(HttpClient);

  listAssignableProfessionals(): Observable<ApiEnvelope<AssignableProfessional[]>> {
    return this.http.get<ApiEnvelope<AssignableProfessional[]>>('/api/v1/schedule/assignable-professionals');
  }
}
