import { View } from 'react-native';
import { colors } from '@/theme';

/**
 * Boot route. The root layout's auth gate redirects to (auth) or (app) once the
 * session resolves; until then the native splash covers this blank frame.
 */
export default function Index() {
  return <View style={{ flex: 1, backgroundColor: colors.background }} />;
}
