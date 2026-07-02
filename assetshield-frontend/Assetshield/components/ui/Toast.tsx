import { Ionicons } from '@expo/vector-icons';
import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { Animated, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors, radius, spacing } from '@/theme';
import { Text } from './Text';

type ToastKind = 'success' | 'error' | 'info';
type ToastState = { message: string; kind: ToastKind } | null;

const ToastContext = createContext<{ show: (message: string, kind?: ToastKind) => void }>({ show: () => {} });

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toast, setToast] = useState<ToastState>(null);
  const insets = useSafeAreaInsets();
  const y = useRef(new Animated.Value(-80)).current;
  const opacity = useRef(new Animated.Value(0)).current;
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const hide = useCallback(() => {
    Animated.parallel([
      Animated.timing(y, { toValue: -80, duration: 220, useNativeDriver: true }),
      Animated.timing(opacity, { toValue: 0, duration: 220, useNativeDriver: true }),
    ]).start(() => setToast(null));
  }, [y, opacity]);

  const show = useCallback(
    (message: string, kind: ToastKind = 'success') => {
      setToast({ message, kind });
      if (timer.current) clearTimeout(timer.current);
      Animated.parallel([
        Animated.spring(y, { toValue: 0, useNativeDriver: true, bounciness: 8 }),
        Animated.timing(opacity, { toValue: 1, duration: 220, useNativeDriver: true }),
      ]).start();
      timer.current = setTimeout(hide, 2600);
    },
    [y, opacity, hide],
  );

  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current);
    },
    [],
  );

  const meta = {
    success: { icon: 'checkmark-circle' as const, bg: colors.success },
    error: { icon: 'alert-circle' as const, bg: colors.error },
    info: { icon: 'information-circle' as const, bg: colors.primary },
  }[toast?.kind ?? 'success'];

  return (
    <ToastContext.Provider value={{ show }}>
      {children}
      {toast ? (
        <Animated.View
          pointerEvents="none"
          style={{
            position: 'absolute',
            top: insets.top + spacing.sm,
            left: spacing.lg,
            right: spacing.lg,
            transform: [{ translateY: y }],
            opacity,
          }}
        >
          <View
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              gap: spacing.md,
              backgroundColor: meta.bg,
              borderRadius: radius.md,
              paddingVertical: spacing.md,
              paddingHorizontal: spacing.lg,
              shadowColor: colors.black,
              shadowOpacity: 0.2,
              shadowRadius: 10,
              shadowOffset: { width: 0, height: 4 },
              elevation: 8,
            }}
          >
            <Ionicons name={meta.icon} size={20} color={colors.white} />
            <Text variant="bodyMd" weight="semibold" color={colors.white} style={{ flex: 1 }}>
              {toast.message}
            </Text>
          </View>
        </Animated.View>
      ) : null}
    </ToastContext.Provider>
  );
}

export function useToast() {
  return useContext(ToastContext);
}
