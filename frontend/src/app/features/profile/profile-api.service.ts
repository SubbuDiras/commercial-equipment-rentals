import { inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

export interface MyProfile {
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  roles: string[];
  memberSince: string;
  /** After an update only: whether the sign-in service (Keycloak) took the new name too. */
  signInNameUpdated: boolean | null;
  /** Short-lived link to the profile photo, or null. */
  photoUrl: string | null;
}

export interface MyProfileUpdate {
  firstName: string;
  lastName: string;
  phone: string;
}

@Injectable({ providedIn: 'root' })
export class ProfileApiService {
  private readonly http = inject(HttpClient);

  /** The signed-in user's photo, shared with the header; kept current by every profile read and change. */
  readonly photoUrl = signal<string | null>(null);

  get(): Observable<MyProfile> {
    return this.http.get<MyProfile>('/api/v1/me').pipe(tap(profile => this.photoUrl.set(profile.photoUrl)));
  }

  update(update: MyProfileUpdate): Observable<MyProfile> {
    return this.http.put<MyProfile>('/api/v1/me', update).pipe(tap(profile => this.photoUrl.set(profile.photoUrl)));
  }

  updatePhoto(photo: File): Observable<MyProfile> {
    const body = new FormData();
    body.append('photo', photo, photo.name);
    return this.http.put<MyProfile>('/api/v1/me/photo', body).pipe(tap(profile => this.photoUrl.set(profile.photoUrl)));
  }

  removePhoto(): Observable<MyProfile> {
    return this.http.delete<MyProfile>('/api/v1/me/photo').pipe(tap(profile => this.photoUrl.set(profile.photoUrl)));
  }
}
