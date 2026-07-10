package org.sopt.ssingserver.domain.lesson.realtime;

public interface LessonRealtimeNotifier {

    void send(LessonRealtimeDelivery delivery);
}
