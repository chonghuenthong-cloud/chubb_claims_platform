import { Pipe, PipeTransform } from '@angular/core';

/**
 * Formats a duration given in hours: under 1 day as hours ("5.2 h"), otherwise as days ("1.8 days").
 * Shows "–" when there is no value (nothing to average yet).
 */
@Pipe({ name: 'duration' })
export class DurationPipe implements PipeTransform {
  transform(hours: number | null | undefined): string {
    if (hours == null) return '–';
    if (hours < 24) return `${hours.toFixed(1)} h`;
    const days = hours / 24;
    return `${days.toFixed(1)} ${days < 1.05 ? 'day' : 'days'}`;
  }
}
