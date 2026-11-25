package com.alfre.macrobuilder;

import static com.alfre.macrobuilder.MacroText.tr;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TextNumberObservation {
   private final Spec spec;
   private boolean present;
   private boolean observed;
   private boolean invalid;
   private double value;
   private long changedAtMs;

   TextNumberObservation(Spec spec) {
      this.spec = spec;
   }

   void observeCurrent(List<String> lines, long nowMs) {
      Double number = null;
      for (String line : lines) {
         Matcher matcher = this.spec.pattern().matcher(plainText(line));
         while (matcher.find()) {
            Double captured = capturedNumber(matcher);
            if (captured == null || number != null && Double.compare(number, captured) != 0) {
               this.present = false;
               this.invalid = true;
               return;
            }
            number = captured;
         }
      }
      if (number == null) {
         this.present = false;
         this.invalid = false;
         return;
      }
      // 同一个显示值被反复读取不代表数据更新；新鲜度以数值变化为准。
      if (!this.observed || Double.compare(number, this.value) != 0) {
         this.value = number;
         this.changedAtMs = nowMs;
      }
      this.present = true;
      this.observed = true;
      this.invalid = false;
   }

   void recordMessage(String message, long nowMs) {
      Matcher matcher = this.spec.pattern().matcher(plainText(message));
      while (matcher.find()) {
         Double number = capturedNumber(matcher);
         if (number == null) {
            this.present = false;
            this.invalid = true;
            return;
         }
         double previous = this.valueAt(nowMs);
         this.value = !this.observed ? number : switch (this.spec.mode()) {
            case LATEST -> number;
            case MIN -> Math.min(previous, number);
            case MAX -> Math.max(previous, number);
         };
         this.changedAtMs = nowMs;
         this.present = true;
         this.observed = true;
         this.invalid = false;
      }
   }

   Snapshot snapshot(long nowMs) {
      double ageSeconds = Math.max(0L, nowMs - this.changedAtMs) / 1000.0;
      double current = this.valueAt(nowMs);
      return new Snapshot(this.present && Double.isFinite(current), current, ageSeconds, this.invalid || this.present && !Double.isFinite(current));
   }

   void clear() {
      this.present = false;
      this.observed = false;
      this.invalid = false;
   }

   private double valueAt(long nowMs) {
      return this.value + this.spec.rate() * (Math.max(0L, nowMs - this.changedAtMs) / 1000.0);
   }

   private static Double capturedNumber(Matcher matcher) {
      String text = matcher.group(1);
      if (text == null) {
         return null;
      }
      try {
         double value = Double.parseDouble(text);
         return Double.isFinite(value) ? value : null;
      } catch (NumberFormatException error) {
         return null;
      }
   }

   private static String plainText(String text) {
      return text == null ? "" : text.replaceAll("(?i)§[0-9a-fk-orx]", "");
   }

   record Snapshot(boolean present, double value, double ageSeconds, boolean invalid) {
   }

   enum Mode {
      LATEST, MIN, MAX
   }

   record Key(boolean chat, String regex, double rate, Mode mode) {
   }

   record Spec(boolean chat, Pattern pattern, String operator, double threshold, double staleSeconds,
      double rate, Mode mode, boolean missing) {
      Key key() {
         return new Key(this.chat, this.pattern.pattern(), this.rate, this.mode);
      }

      boolean matches(Snapshot snapshot) {
         if (snapshot.invalid()) {
            return false;
         }
         if (!snapshot.present() || this.staleSeconds > 0 && snapshot.ageSeconds() > this.staleSeconds) {
            return this.missing;
         }
         return switch (this.operator) {
            case "gt" -> snapshot.value() > this.threshold;
            case "gte" -> snapshot.value() >= this.threshold;
            case "lt" -> snapshot.value() < this.threshold;
            case "lte" -> snapshot.value() <= this.threshold;
            case "eq" -> snapshot.value() == this.threshold;
            case "ne" -> snapshot.value() != this.threshold;
            default -> throw new IllegalStateException(tr("macro_builder.number.unsupported_number_comparison", this.operator));
         };
      }

      static Spec parse(boolean chat, String regex, String options) {
         Map<String, String> values = parseOptions(options);
         if (regex.isBlank()) {
            throw new IllegalArgumentException(tr("macro_builder.number.number_regex_cannot_be_blank"));
         }
         Pattern pattern = Pattern.compile(regex);
         if (pattern.matcher("").groupCount() < 1) {
            throw new IllegalArgumentException(tr("macro_builder.number.number_regex_needs_capture_group_1_containing_a_number"));
         }
         String operator = values.get("op");
         if (!Set.of("gt", "gte", "lt", "lte", "eq", "ne").contains(operator)) {
            throw new IllegalArgumentException(tr("macro_builder.number.number_comparison_op_must_be_gt_gte_lt_lte_eq_ne"));
         }
         double threshold = numberOption("value", values.get("value"));
         double stale = numberOption("stale", values.get("stale"));
         double rate = numberOption("rate", values.get("rate"));
         if (stale < 0) {
            throw new IllegalArgumentException(tr("macro_builder.number.number_comparison_stale_must_be_nonnegative_0_disables_expiry"));
         }
         Mode mode = switch (values.get("mode")) {
            case "latest" -> Mode.LATEST;
            case "min" -> Mode.MIN;
            case "max" -> Mode.MAX;
            default -> throw new IllegalArgumentException(tr("macro_builder.number.number_comparison_mode_must_be_latest_min_max"));
         };
         if (!chat && mode != Mode.LATEST) {
            throw new IllegalArgumentException(tr("macro_builder.number.scoreboard_numbers_use_mode_latest"));
         }
         String missing = values.get("missing");
         if (!"true".equals(missing) && !"false".equals(missing)) {
            throw new IllegalArgumentException(tr("macro_builder.number.number_comparison_missing_must_be_true_false"));
         }
         return new Spec(chat, pattern, operator, threshold, stale, rate, mode, Boolean.parseBoolean(missing));
      }

      private static Map<String, String> parseOptions(String options) {
         List<String> required = List.of("op", "value", "stale", "rate", "mode", "missing");
         Map<String, String> values = new HashMap<>();
         for (String option : options.trim().split("\\s+")) {
            if (option.isBlank()) {
               continue;
            }
            String[] parts = option.split("=", 2);
            if (parts.length != 2 || parts[1].isBlank()) {
               throw new IllegalArgumentException(tr("macro_builder.number.number_comparison_options_must_use_key_value"));
            }
            if (!required.contains(parts[0])) {
               throw new IllegalArgumentException(tr("macro_builder.number.unknown_number_comparison_option", parts[0]));
            }
            if (values.putIfAbsent(parts[0], parts[1]) != null) {
               throw new IllegalArgumentException(tr("macro_builder.number.duplicate_number_comparison_option", parts[0]));
            }
         }
         for (String key : required) {
            if (!values.containsKey(key)) {
               throw new IllegalArgumentException(tr("macro_builder.number.missing_number_comparison_option", key));
            }
         }
         return values;
      }

      private static double numberOption(String key, String text) {
         try {
            double value = Double.parseDouble(text);
            if (!Double.isFinite(value)) {
               throw new IllegalArgumentException(tr("macro_builder.number.number_comparison_must_be_finite", key));
            }
            return value;
         } catch (NumberFormatException error) {
            throw new IllegalArgumentException(tr("macro_builder.number.number_comparison_must_be_numeric", key), error);
         }
      }
   }
}
