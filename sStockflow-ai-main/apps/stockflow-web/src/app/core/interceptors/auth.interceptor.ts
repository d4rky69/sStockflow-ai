import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const accessToken = auth.accessToken();
  const user = auth.user();
  const setHeaders: Record<string, string> = {
    'X-StockFlow-User-ID': user?.id ?? 'local-prototype-user'
  };
  if (user?.email) setHeaders['X-StockFlow-User-Email'] = user.email;
  if (accessToken) setHeaders['Authorization'] = `Bearer ${accessToken}`;

  return next(request.clone({
    setHeaders
  }));
};
