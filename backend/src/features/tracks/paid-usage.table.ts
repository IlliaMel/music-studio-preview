import { sql } from 'drizzle-orm'
import { check, date, integer, pgTable } from 'drizzle-orm/pg-core'

/** Composition and reference-upload reservations; account deletion cannot erase spent usage. */
export const paidGenerationDailyUsage = pgTable(
  'paid_generation_daily_usage',
  {
    day: date('day').primaryKey(),
    // Historical column name: also includes one unit per newly reserved upstream upload.
    reservedVariants: integer('reserved_variants').notNull(),
  },
  (table) => [
    check('paid_generation_daily_usage_nonnegative', sql`${table.reservedVariants} >= 0`),
  ],
)
