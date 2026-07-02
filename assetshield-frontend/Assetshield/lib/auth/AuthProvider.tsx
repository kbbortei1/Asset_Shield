import { useQueryClient } from '@tanstack/react-query';
import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import {
  authApi,
  clearTokens,
  getRefreshToken,
  hasSession,
  loadTokens,
  LoginRequest,
  RegisterAgentRequest,
  RegisterRequest,
  setSessionExpiredHandler,
  setTokens,
  usersApi,
  UserProfile,
  VerifyOtpRequest,
} from '@/lib/api';
import { listenForTokenRotation, registerPushToken, unregisterPushToken } from '@/lib/push/push';

type Status = 'loading' | 'authenticated' | 'unauthenticated';

type AuthContextValue = {
  status: Status;
  user: UserProfile | null;
  /** true right after a fresh registration when the owner hasn't done KYC yet */
  pendingOnboarding: boolean;
  clearOnboarding: () => void;
  register: (body: RegisterRequest) => Promise<void>;
  registerAgent: (body: RegisterAgentRequest) => Promise<void>;
  verifyOtp: (body: VerifyOtpRequest) => Promise<UserProfile>;
  resendOtp: (phoneNumber: string) => Promise<void>;
  login: (body: LoginRequest) => Promise<UserProfile>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<Status>('loading');
  const [user, setUser] = useState<UserProfile | null>(null);
  const [pendingOnboarding, setPendingOnboarding] = useState(false);
  const qc = useQueryClient();
  const tokenRotationUnsub = useRef<(() => void) | null>(null);

  const onAuthenticated = useCallback(async (profile: UserProfile) => {
    setUser(profile);
    setStatus('authenticated');
    // Push lifecycle: register the FCM token + listen for rotation.
    registerPushToken();
    tokenRotationUnsub.current?.();
    tokenRotationUnsub.current = listenForTokenRotation();
  }, []);

  const goUnauthenticated = useCallback(async () => {
    tokenRotationUnsub.current?.();
    tokenRotationUnsub.current = null;
    setUser(null);
    setStatus('unauthenticated');
    qc.clear();
  }, [qc]);

  // Boot: restore tokens, fetch profile.
  useEffect(() => {
    (async () => {
      await loadTokens();
      if (await hasSession()) {
        try {
          const profile = await usersApi.me();
          await onAuthenticated(profile);
        } catch {
          await clearTokens();
          setStatus('unauthenticated');
        }
      } else {
        setStatus('unauthenticated');
      }
    })();
  }, [onAuthenticated]);

  // Force re-login when the interceptor reports a dead session.
  useEffect(() => {
    setSessionExpiredHandler(() => {
      goUnauthenticated();
    });
    return () => setSessionExpiredHandler(null);
  }, [goUnauthenticated]);

  const register = useCallback(async (body: RegisterRequest) => {
    await authApi.register(body);
  }, []);

  const registerAgent = useCallback(async (body: RegisterAgentRequest) => {
    await authApi.registerAgent(body);
  }, []);

  const verifyOtp = useCallback(
    async (body: VerifyOtpRequest) => {
      const result = await authApi.verifyOtp(body);
      await setTokens({ accessToken: result.accessToken, refreshToken: result.refreshToken });
      const profile = await usersApi.me(); // token response carries only a partial user summary
      // Fresh owner without KYC → route through onboarding (agents await verification instead).
      setPendingOnboarding(profile.role === 'OWNER' && !profile.ghanaCardUploaded);
      await onAuthenticated(profile);
      return profile;
    },
    [onAuthenticated],
  );

  const resendOtp = useCallback(async (phoneNumber: string) => {
    await authApi.resendOtp(phoneNumber);
  }, []);

  const login = useCallback(
    async (body: LoginRequest) => {
      const result = await authApi.login(body);
      await setTokens({ accessToken: result.accessToken, refreshToken: result.refreshToken });
      const profile = await usersApi.me();
      setPendingOnboarding(false); // returning user — no onboarding
      await onAuthenticated(profile);
      return profile;
    },
    [onAuthenticated],
  );

  const logout = useCallback(async () => {
    const refreshToken = await getRefreshToken();
    await unregisterPushToken();
    if (refreshToken) {
      try {
        await authApi.logout(refreshToken);
      } catch {
        // ignore — we clear locally regardless
      }
    }
    await clearTokens();
    await goUnauthenticated();
  }, [goUnauthenticated]);

  const refreshUser = useCallback(async () => {
    try {
      setUser(await usersApi.me());
    } catch {
      // ignore
    }
  }, []);

  const clearOnboarding = useCallback(() => setPendingOnboarding(false), []);

  return (
    <AuthContext.Provider
      value={{ status, user, pendingOnboarding, clearOnboarding, register, registerAgent, verifyOtp, resendOtp, login, logout, refreshUser }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
