import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface MyProfile {
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  roles: string[];
  memberSince: string;
  /** After an update only: whether the sign-in service (Keycloak) took the new name too. */
  signInNameUpdated: boolean | null;
}

export interface MyProfileUpdate {
  firstName: string;
  lastName: string;
  phone: string;
}

@Injectable({ providedIn: 'root' })
export class ProfileApiService {
  private readonly http = inject(HttpClient);

  get(): Observable<MyProfile> {
    return this.http.get<MyProfile>('/api/v1/me');
  }

  update(update: MyProfileUpdate): Observable<MyProfile> {
    return this.http.put<MyProfile>('/api/v1/me', update);
  }
}
