import { QueryClient } from '@tanstack/react-query';
import { isApiError } from '@/lib/api';

/** Shared React Query client. Don't retry auth/permission/validation errors. */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error) => {
        if (isApiError(error)) {
          if (error.httpStatus === 0) return failureCount < 2; // network: retry a couple times
          if ([400, 401, 403, 404, 409].includes(error.httpStatus)) return false;
        }
        return failureCount < 1;
      },
      staleTime: 30_000,
      refetchOnWindowFocus: false,
    },
    mutations: { retry: false },
  },
});
