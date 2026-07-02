import { Redirect } from 'expo-router';

/** Entry into the authenticated area → role-aware tabs (home dispatches by role). */
export default function AppIndex() {
  return <Redirect href={'/(app)/(tabs)/home' as never} />;
}
