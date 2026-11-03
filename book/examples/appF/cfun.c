/* C side of the example cfun.a68. */
#include <stddef.h>
#include <stdint.h>

int calls = 0;

/* The number of vowels in an Algol 68 string: the characters (UCS-4),
   their number and the distance in bytes between two of them. */
int vowels (const uint32_t *s, size_t len, size_t stride)
{
  int n = 0;
  calls++;
  for (size_t i = 0; i < len; i++)
    {
      uint32_t c = *(const uint32_t *) ((const char *) s + i * stride);
      if (c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u')
        n++;
    }
  return n;
}

/* Calls f for 1, ..., n and returns the sum of the results. */
int sum_of (int (*f) (int), int n)
{
  int s = 0;
  calls++;
  for (int i = 1; i <= n; i++)
    s += f (i);
  return s;
}
