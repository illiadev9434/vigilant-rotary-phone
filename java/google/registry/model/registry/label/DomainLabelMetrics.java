// Copyright 2017 The Nomulus Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package google.registry.model.registry.label;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import google.registry.monitoring.metrics.EventMetric;
import google.registry.monitoring.metrics.IncrementableMetric;
import google.registry.monitoring.metrics.LabelDescriptor;
import google.registry.monitoring.metrics.MetricRegistryImpl;

/** Instrumentation for reserved lists. */
class DomainLabelMetrics {

  @AutoValue
  abstract static class MetricsReservedListMatch {
    static MetricsReservedListMatch create(
        String reservedListName, ReservationType reservationType) {
      return new AutoValue_DomainLabelMetrics_MetricsReservedListMatch(
          reservedListName, reservationType);
    }

    abstract String reservedListName();
    abstract ReservationType reservationType();
  }

  /**
   * Labels attached to {@link #reservedListChecks} and {@link #reservedListProcessingTimes}
   * metrics.
   *
   * <p>A domain name can be matched by multiple reserved lists. To keep the metrics useful by
   * emitting only one metric result for each check, while avoiding potential combinatorial
   * explosion if all the matching lists and reservation types were to be displayed, we store as
   * labels only the number of matching lists, along with the most severe match found. Note that
   * "most severe" may not be meaningful, and this should only be treated as "one of the matches
   * that we found". But we might as well make it as useful as possible.
   */
  private static final ImmutableSet<LabelDescriptor> RESERVED_LIST_LABEL_DESCRIPTORS =
      ImmutableSet.of(
          LabelDescriptor.create("tld", "TLD"),
          LabelDescriptor.create("reserved_list_count", "Number of matching reserved lists."),
          LabelDescriptor.create("most_severe_reserved_list", "Reserved list name, if any."),
          LabelDescriptor.create("most_severe_reservation_type", "Type of reservation found."));

  /** Labels attached to {@link #reservedListHits} metric. */
  private static final ImmutableSet<LabelDescriptor> RESERVED_LIST_HIT_LABEL_DESCRIPTORS =
      ImmutableSet.of(
          LabelDescriptor.create("tld", "TLD"),
          LabelDescriptor.create("reserved_list", "Reserved list name."),
          LabelDescriptor.create("reservation_type", "Type of reservation found."));

  /** Metric counting the number of times a label was checked against all reserved lists. */
  @VisibleForTesting
  static final IncrementableMetric reservedListChecks =
      MetricRegistryImpl.getDefault()
          .newIncrementableMetric(
              "/domain_label/reserved/checks",
              "Count of reserved list checks",
              "count",
              RESERVED_LIST_LABEL_DESCRIPTORS);

  /** Metric recording the amount of time required to check a label against all reserved lists. */
  @VisibleForTesting
  static final EventMetric reservedListProcessingTimes =
      MetricRegistryImpl.getDefault()
          .newEventMetric(
              "/domain_label/reserved/processing_time",
              "Reserved list check processing time",
              "milliseconds",
              RESERVED_LIST_LABEL_DESCRIPTORS,
              EventMetric.DEFAULT_FITTER);

  /**
   * Metric recording the number of times a label was found in a reserved list.
   *
   * <p>Each time a label is checked, and a list associated with the TLD contains that label, that
   * count is incremented. A label can be found in more than one list, which would result in a
   * single increment of {@link #reservedListChecks}, but multiple increments of {@link
   * #reservedListHits}. It can of course also match zero lists, which would still result in a
   * single increment of {@link #reservedListChecks}, but no increments of {@link
   * #reservedListHits}.
   */
  @VisibleForTesting
  static final IncrementableMetric reservedListHits =
      MetricRegistryImpl.getDefault()
          .newIncrementableMetric(
              "/domain_label/reserved/hits",
              "Count of reserved list hits",
              "count",
              RESERVED_LIST_HIT_LABEL_DESCRIPTORS);

  /** Update all three reserved list metrics. */
  static void recordReservedListCheckOutcome(
      String tld, ImmutableSet<MetricsReservedListMatch> matches, double elapsedMillis) {
    MetricsReservedListMatch mostSevereMatch = null;
    for (MetricsReservedListMatch match : matches) {
      reservedListHits.increment(tld, match.reservedListName(), match.reservationType().toString());
      if ((mostSevereMatch == null)
          || (match.reservationType().compareTo(mostSevereMatch.reservationType()) > 0)) {
        mostSevereMatch = match;
      }
    }
    String matchCount = String.valueOf(matches.size());
    String mostSevereReservedList =
        matches.isEmpty() ? "(none)" : mostSevereMatch.reservedListName();
    String mostSevereReservationType =
        (matches.isEmpty() ? ReservationType.UNRESERVED : mostSevereMatch.reservationType())
            .toString();
    reservedListChecks.increment(
        tld, matchCount, mostSevereReservedList, mostSevereReservationType);
    reservedListProcessingTimes.record(
        elapsedMillis, tld, matchCount, mostSevereReservedList, mostSevereReservationType);
  }
}
