import NetInfo from '@react-native-community/netinfo';
import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { failedCount, flushQueue, pendingCount, retryFailed } from './queue';

type OfflineContextValue = {
  isOnline: boolean;
  pending: number;
  /** Uploads that exhausted their retries on real server errors. */
  failed: number;
  refreshPending: () => Promise<void>;
  flushNow: () => Promise<void>;
  /** Give failed uploads a fresh set of retries and replay them. */
  retryFailedNow: () => Promise<void>;
};

const OfflineContext = createContext<OfflineContextValue>({
  isOnline: true,
  pending: 0,
  failed: 0,
  refreshPending: async () => {},
  flushNow: async () => {},
  retryFailedNow: async () => {},
});

export function OfflineProvider({ children }: { children: React.ReactNode }) {
  const [isOnline, setIsOnline] = useState(true);
  const [pending, setPending] = useState(0);
  const [failed, setFailed] = useState(0);
  const wasOnline = useRef(true);

  const refreshPending = useCallback(async () => {
    setPending(await pendingCount());
    setFailed(await failedCount());
  }, []);

  const flushNow = useCallback(async () => {
    const res = await flushQueue();
    setPending(res.remaining);
    setFailed(await failedCount());
  }, []);

  const retryFailedNow = useCallback(async () => {
    const res = await retryFailed();
    setPending(res.remaining);
    setFailed(await failedCount());
  }, []);

  useEffect(() => {
    refreshPending();
    const unsub = NetInfo.addEventListener((state) => {
      const online = !!state.isConnected && state.isInternetReachable !== false;
      setIsOnline(online);
      // On the offline→online transition, flush the queue (target ~60s, §5).
      if (online && !wasOnline.current) {
        flushNow();
      }
      wasOnline.current = online;
    });
    return () => unsub();
  }, [refreshPending, flushNow]);

  return (
    <OfflineContext.Provider value={{ isOnline, pending, failed, refreshPending, flushNow, retryFailedNow }}>
      {children}
    </OfflineContext.Provider>
  );
}

export function useOffline(): OfflineContextValue {
  return useContext(OfflineContext);
}
