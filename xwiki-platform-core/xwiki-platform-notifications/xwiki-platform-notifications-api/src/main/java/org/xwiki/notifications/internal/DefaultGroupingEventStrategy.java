/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.notifications.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.eventstream.Event;
import org.xwiki.notifications.CompositeEvent;
import org.xwiki.notifications.GroupingEventStrategy;
import org.xwiki.notifications.NotificationException;

/**
 * Default strategy for grouping event, that reuse the {@link SimilarityCalculator}.
 *
 * @version $Id$
 * @since 15.5RC1
 */
@Component
@Singleton
public class DefaultGroupingEventStrategy implements GroupingEventStrategy
{
    @Inject
    private SimilarityCalculator similarityCalculator;

    private static final class BestSimilarityHolder
    {
        int value;

        CompositeEvent compositeEvent;

        Event event;

        int compositeEventSimilary;

        public boolean isCompositeEventCompatibleWith(Event event)
        {
            // Here we have a composite event made of A and B.
            // - if A is a "create" or an "update" event
            // - if A and B have the same groupId (which means A or B is a "create" or an "update" event basically)
            // - if B has the same type than E
            // (or vice versa)
            // It means the "update" event A has been triggered for technical reason, but the interesting event is
            // B, which we can group with the event E even if it lowers the similarity between the events.
            return compositeEventSimilary >= SimilarityCalculator.SAME_GROUP_ID_AND_DOCUMENT_BUT_DIFFERENT_TYPES
                && compositeEvent.getType().equals(event.getType());
        }
    }

    private void recordEvent(List<BestSimilarityHolder> bestSimilarityHolderList, Event event)
            throws NotificationException
    {
        BestSimilarityHolder bestSimilarityHolder = getBestSimilarity(bestSimilarityHolderList, event);

        if (bestSimilarityHolder != null) {
            if (bestSimilarityHolder.value > bestSimilarityHolder.compositeEventSimilary
                && bestSimilarityHolder.compositeEvent.getEvents().size() > 1) {
                // We have found an event A inside a composite event C1 that have a greater similarity with the event E
                // than the similarity between events (A, B, C) of that composite event (C1).
                //
                // It means we must remove the existing event A from that composite event C1 and create a new composite
                // event C2 made of A and E.
                bestSimilarityHolder.compositeEvent.remove(bestSimilarityHolder.event);

                // Instead of creating a new composite event with A and E, we first look if an other composite event can
                // match with A and E.
                BestSimilarityHolder bestSecondChoice = getBestSimilarity(bestSimilarityHolderList, event);
                if (bestSecondChoice.compositeEvent != null && bestSecondChoice.isCompositeEventCompatibleWith(event)) {
                    // We have found a composite event C2 made of events (X, Y) which have a greater similarity between
                    // themselves than between X and the event E.
                    // It means we cannot add E in C2.
                    // But there is actually an exception:
                    // - if X is a "create" or an "update" event
                    // - if X and Y have the same groupId
                    // - if Y has the same type than E
                    // (or vice versa)
                    // It means the "update" event X has been triggered for technical reason, but the interesting event
                    // is Y, which we can group with the event E.
                    bestSecondChoice.compositeEvent.add(bestSimilarityHolder.event);
                    bestSecondChoice.compositeEvent.add(event);
                    bestSimilarityHolderList.remove(bestSimilarityHolder);
                }
                bestSimilarityHolderList.add(bestSecondChoice);
            } else if (bestSimilarityHolder.value >= bestSimilarityHolder.compositeEventSimilary) {
                // We have found a composite event C1 made of events (A, B, C) which have the same similarity between
                // themselves than between A end E.
                // All we need to do it to add E to C1.
                bestSimilarityHolder.compositeEvent.add(event);
            } else if (bestSimilarityHolder.isCompositeEventCompatibleWith(event)) {
                // We have found a composite event C1 made of events (A, B) which have a greater similarity between
                // themselves than between A and the event E.
                // It means we cannot add E in C1.
                // But there is actually an exception:
                // - if A is a "create" or an "update" event
                // - if A and B have the same groupId
                // - if B has the same type than E
                // (or vice versa)
                // It means the "update" event A has been triggered for technical reason, but the interesting event is
                // B, which we can group with the event E.
                bestSimilarityHolder.compositeEvent.add(event);
            }
        }
        if (bestSimilarityHolder == null) {
            // We haven't found an event that is similar to the current one, so we create a new composite event
            CompositeEvent compositeEvent = new CompositeEvent(event);
            bestSimilarityHolder = new BestSimilarityHolder();
            bestSimilarityHolder.compositeEvent = compositeEvent;
            bestSimilarityHolder.event = event;
            bestSimilarityHolderList.add(bestSimilarityHolder);
        }
    }

    private BestSimilarityHolder getBestSimilarity(List<BestSimilarityHolder> bestSimilarityHolderList, Event event)
    {
        BestSimilarityHolder bestSimilarityHolder = null;

        // Looking for the most similar event inside the existing composite events
        for (BestSimilarityHolder holder : bestSimilarityHolderList) {
            CompositeEvent existingCompositeEvent = holder.compositeEvent;
            for (Event existingEvent : existingCompositeEvent.getEvents()) {
                int similarity = similarityCalculator.computeSimilarity(event, existingEvent);
                if (similarity < holder.compositeEventSimilary) {
                    // Penality
                    similarity -= 5;
                }
                if (similarity > holder.value) {
                    holder.compositeEventSimilary = holder.value;
                    holder.value = similarity;
                    holder.event = existingEvent;
                    bestSimilarityHolder = holder;
                }
            }
        }

        return bestSimilarityHolder;
    }

    @Override
    public List<CompositeEvent> group(List<Event> eventList) throws NotificationException
    {
        List<BestSimilarityHolder> bestSimilarityHolderList = new ArrayList<>();
        for (Event event : eventList) {
            recordEvent(bestSimilarityHolderList, event);
        }

        return bestSimilarityHolderList.stream()
                .map(bestSimilarityHolder -> bestSimilarityHolder.compositeEvent)
                .collect(Collectors.toList());
    }

    @Override
    public void group(List<CompositeEvent> compositeEvents, List<Event> newEvents) throws NotificationException
    {
        for (Event newEvent : newEvents) {
            //recordEvent(compositeEvents, newEvent);
        }
    }
}
