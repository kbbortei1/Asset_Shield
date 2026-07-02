import Svg, { Circle, Defs, G, LinearGradient, Path, Stop } from 'react-native-svg';

/**
 * Vector recreation of the AssetShield GH mark: a royal-blue shield carrying a
 * gold camera-aperture (six swirling blades + hub) with gold corner brackets.
 * Crisp at any size, no raster asset required. For the exact supplied artwork on
 * the launcher icon / native splash, drop the PNG at assets/images/logo.png and
 * point app.json `icon`/splash at it.
 */
export function ShieldLogo({ size = 96 }: { size?: number }) {
  // six aperture blades, each rotated 60°, inner vertex offset for the swirl
  const blades = [0, 60, 120, 180, 240, 300];
  return (
    <Svg width={size} height={size} viewBox="0 0 100 100">
      <Defs>
        <LinearGradient id="shield" x1="0" y1="0" x2="0" y2="1">
          <Stop offset="0" stopColor="#3B7DEB" />
          <Stop offset="1" stopColor="#1E4FC4" />
        </LinearGradient>
        <LinearGradient id="gold" x1="0" y1="0" x2="1" y2="1">
          <Stop offset="0" stopColor="#F6CE58" />
          <Stop offset="1" stopColor="#D8982C" />
        </LinearGradient>
      </Defs>

      {/* shield body */}
      <Path
        d="M50 5 L88 18 L88 48 C88 72 72 88 50 96 C28 88 12 72 12 48 L12 18 Z"
        fill="url(#shield)"
      />

      {/* gold corner brackets */}
      <Path d="M26 26 L26 34 M26 26 L34 26" stroke="#F4C24B" strokeWidth="3.2" strokeLinecap="round" fill="none" />
      <Path d="M74 26 L74 34 M74 26 L66 26" stroke="#F4C24B" strokeWidth="3.2" strokeLinecap="round" fill="none" />

      {/* aperture blades */}
      <G originX="50" originY="52">
        {blades.map((deg) => (
          <Path
            key={deg}
            d="M58 52 L50 24 L80 38 Z"
            fill="url(#gold)"
            stroke="#1E4FC4"
            strokeWidth="1.6"
            strokeLinejoin="round"
            transform={`rotate(${deg} 50 52)`}
          />
        ))}
        {/* center hub */}
        <Circle cx="50" cy="52" r="9" fill="#F4C24B" stroke="#D8982C" strokeWidth="1.2" />
      </G>
    </Svg>
  );
}
