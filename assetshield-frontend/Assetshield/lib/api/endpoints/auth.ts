import { api } from '../client';
import {
  AuthResult,
  LoginRequest,
  RegisterAgentRequest,
  RegisterRequest,
  VerifyOtpRequest,
} from '../models';

export const authApi = {
  register: (body: RegisterRequest) => api.post<{ phoneNumber: string }>('/auth/register', body, { auth: false }),
  registerAgent: (body: RegisterAgentRequest) =>
    api.post<{ phoneNumber: string }>('/auth/register-agent', body, { auth: false }),
  verifyOtp: (body: VerifyOtpRequest) => api.post<AuthResult>('/auth/verify-otp', body, { auth: false }),
  resendOtp: (phoneNumber: string) => api.post<void>('/auth/resend-otp', { phoneNumber }, { auth: false }),
  login: (body: LoginRequest) => api.post<AuthResult>('/auth/login', body, { auth: false }),
  logout: (refreshToken: string) => api.post<void>('/auth/logout', { refreshToken }),
};
