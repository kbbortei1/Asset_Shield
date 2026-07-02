import NetInfo from '@react-native-community/netinfo';
import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { flushQueue, pendingCount } from './queue';

type OfflineContextValue = {
  isOnline: boolean;
  pending: number;
  refreshPending: () => Promise<void>;
  flushNow: () => Promise<void>;
};

const OfflineContext = createContext<OfflineContextValue>({
  isOnline: true,
  pending: 0,
  refreshPending: async () => {},
  flushNow: async () => {},
});

export function OfflineProvider({ children }: { children: React.ReactNode }) {
  const [isOnline, setIsOnline] = useState(true);
  const [pending, setPending] = useState(0);
  const wasOnline = useRef(true);

  const refreshPending = useCallback(async () => {
    setPending(await pendingCount());
  }, []);

  const flushNow = useCallback(async () => {
    const res = await flushQueue();
    setPending(res.remaining);
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
    <OfflineContext.Provider value={{ isOnline, pending, refreshPending, flushNow }}>
      {children}
    </OfflineContext.Provider>
  );
}

export function useOffline(): OfflineContextValue {
  return useContext(OfflineContext);
}
