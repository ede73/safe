package fi.iki.ede.statemachine

class AllowedEvents(private vararg val eventList: Event, val events: Set<Event> = eventList.toSet())
