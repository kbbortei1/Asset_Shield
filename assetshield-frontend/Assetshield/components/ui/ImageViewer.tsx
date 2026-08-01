import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { createContext, useCallback, useContext, useState } from 'react';
import { Dimensions, Modal, Pressable, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useSharedValue, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { resolveMediaUrl } from '@/lib/api';

const ImageViewerContext = createContext<{ open: (uri?: string | null) => void }>({ open: () => {} });

export function ImageViewerProvider({ children }: { children: React.ReactNode }) {
  const [uri, setUri] = useState<string | null>(null);
  const open = useCallback((u?: string | null) => {
    const resolved = resolveMediaUrl(u);
    if (resolved) setUri(resolved);
  }, []);
  return (
    <ImageViewerContext.Provider value={{ open }}>
      {children}
      <ZoomModal uri={uri} onClose={() => setUri(null)} />
    </ImageViewerContext.Provider>
  );
}

export function useImageViewer() {
  return useContext(ImageViewerContext);
}

const { width, height } = Dimensions.get('window');

function ZoomModal({ uri, onClose }: { uri: string | null; onClose: () => void }) {
  const insets = useSafeAreaInsets();
  const scale = useSharedValue(1);
  const savedScale = useSharedValue(1);
  const tx = useSharedValue(0);
  const ty = useSharedValue(0);
  const sx = useSharedValue(0);
  const sy = useSharedValue(0);

  const reset = () => {
    scale.value = withTiming(1);
    savedScale.value = 1;
    tx.value = withTiming(0);
    ty.value = withTiming(0);
    sx.value = 0;
    sy.value = 0;
  };

  const pinch = Gesture.Pinch()
    .onUpdate((e) => {
      scale.value = Math.max(1, savedScale.value * e.scale);
    })
    .onEnd(() => {
      savedScale.value = scale.value;
    });

  const pan = Gesture.Pan()
    .onUpdate((e) => {
      tx.value = sx.value + e.translationX;
      ty.value = sy.value + e.translationY;
    })
    .onEnd(() => {
      sx.value = tx.value;
      sy.value = ty.value;
    });

  const doubleTap = Gesture.Tap()
    .numberOfTaps(2)
    .onEnd(() => {
      if (scale.value > 1) {
        scale.value = withTiming(1);
        savedScale.value = 1;
        tx.value = withTiming(0);
        ty.value = withTiming(0);
        sx.value = 0;
        sy.value = 0;
      } else {
        scale.value = withTiming(2.5);
        savedScale.value = 2.5;
      }
    });

  const composed = Gesture.Simultaneous(pinch, pan, doubleTap);

  const style = useAnimatedStyle(() => ({
    transform: [{ translateX: tx.value }, { translateY: ty.value }, { scale: scale.value }],
  }));

  const close = () => {
    reset();
    onClose();
  };

  return (
    <Modal visible={!!uri} transparent animationType="fade" onRequestClose={close}>
      <View style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.96)' }}>
        <Pressable
          onPress={close}
          hitSlop={16}
          accessibilityRole="button"
          accessibilityLabel="Close image"
          style={{ position: 'absolute', top: insets.top + 8, right: 16, zIndex: 2, padding: 8 }}
        >
          <Ionicons name="close" size={30} color="#FFFFFF" />
        </Pressable>
        <GestureDetector gesture={composed}>
          <Animated.View style={[{ flex: 1, alignItems: 'center', justifyContent: 'center' }, style]}>
            {uri ? (
              <Image source={{ uri }} style={{ width, height: height * 0.8 }} contentFit="contain" />
            ) : null}
          </Animated.View>
        </GestureDetector>
      </View>
    </Modal>
  );
}
