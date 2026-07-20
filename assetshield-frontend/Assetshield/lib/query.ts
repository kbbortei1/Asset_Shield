import NetInfo from '@react-native-community/netinfo';
import { focusManager, onlineManager, QueryClient } from '@tanstack/react-query';
import { AppState } from 'react-native';
import { isApiError } from '@/lib/api';

// React Query can't detect connectivity or foregrounding in React Native by
// itself - wire its managers to NetInfo and AppState so queries pause while
// offline, refetch when the connection returns, and refresh stale data when
// the app comes back to the foreground.
onlineManager.setEventListener((setOnline) =>
  NetInfo.addEventListener((state) => {
    setOnline(!!state.isConnected && state.isInternetReachable !== false);
  }),
);

AppState.addEventListener('change', (status) => {
  focusManager.setFocused(status === 'active');
});

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
