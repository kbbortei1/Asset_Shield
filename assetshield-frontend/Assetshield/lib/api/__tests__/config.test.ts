import { API_BASE_URL, API_ORIGIN, resolveMediaUrl } from '../config';

describe('config', () => {
  it('API_ORIGIN strips the /api/vN suffix from the base URL', () => {
    expect(API_BASE_URL.startsWith(API_ORIGIN)).toBe(true);
    expect(API_ORIGIN).not.toMatch(/\/api\/v\d+$/);
  });

  describe('resolveMediaUrl', () => {
    it('returns undefined for missing values', () => {
      expect(resolveMediaUrl(undefined)).toBeUndefined();
      expect(resolveMediaUrl(null)).toBeUndefined();
      expect(resolveMediaUrl('')).toBeUndefined();
    });

    it('passes absolute URLs through unchanged', () => {
      const url = 'https://cdn.example.com/bucket/photo.jpg?sig=abc';
      expect(resolveMediaUrl(url)).toBe(url);
      expect(resolveMediaUrl('http://other.host/x.png')).toBe('http://other.host/x.png');
    });

    it('prefixes relative signed URLs with the API origin (local storage)', () => {
      expect(resolveMediaUrl('/api/v1/public/damage-files/tok123')).toBe(
        `${API_ORIGIN}/api/v1/public/damage-files/tok123`,
      );
    });

    it('normalizes a missing leading slash', () => {
      expect(resolveMediaUrl('api/v1/files/x')).toBe(`${API_ORIGIN}/api/v1/files/x`);
    });
  });
});
