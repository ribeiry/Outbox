package com.ribeiry.outboxEvent;

import com.ribeiry.kafka.KafkaPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OutboxPoller {

    private final OutboxEventsRepository repository;
    private final KafkaPublisher kafkaPublisher;
    private final int maxTentativas;
    @Value("${outbox.time.to-reprocessing:15000}")
    public int Timereprocessing;
    private final Map<UUID, Instant> backoffMap = new ConcurrentHashMap<>();


    public OutboxPoller(OutboxEventsRepository repository, KafkaPublisher kafkaPublisher,
                        @Value("${outbox.max-tentativas:3}") int maxTentativas) {
        this.repository = repository;
        this.kafkaPublisher = kafkaPublisher;
        this.maxTentativas = maxTentativas;

    }

    @Scheduled(fixedDelay = 5000)
    public void poll() {
        processar(repository.findByStatus(OutboxEvents.Status.PENDING));
        List<OutboxEvents> failed = repository.findByStatusAndTentativasLessThan(
                OutboxEvents.Status.FAILED,
                        maxTentativas);
        processar(failed.stream().filter(this::prontoParaRetry).toList());

    }

    @Scheduled(fixedDelay = 5000)
    public void reprocessingSchedule(){
        reprocessing(repository.findByStatusAndCriadoEmBefore(OutboxEvents.Status.PROCESSING,
                LocalDateTime.now().minusMinutes(Timereprocessing)));
    }

    private  void reprocessing(List<OutboxEvents> eventos) {
        for(OutboxEvents evento: eventos){
            try{
                evento.setStatus(OutboxEvents.Status.PENDING);
                System.out.println("[POLLER] PENDING: " + evento.getId());
                repository.save(evento);
            }
            catch (Exception e){
                evento.setStatus(OutboxEvents.Status.FAILED);
                System.err.println("[POLLER] FAILED (" + evento.getId());
            }

        }
    }

    private void processar(List<OutboxEvents> eventos) {
        if(idempotencia(eventos)){
            for (OutboxEvents evento : eventos) {

                try{
                    kafkaPublisher.publish("pedidos-eventos", evento.getAggregateId(), evento.getPayload());
                    evento.setStatus(OutboxEvents.Status.PROCESSED);
                    repository.save(evento);
                    System.out.println("[POLLER] PROCESSED: " + evento.getId());
                }
                catch (Exception e) {
                    evento.setTentativas(evento.getTentativas() + 1);
                    if (evento.getTentativas() >= maxTentativas) {
                        evento.setStatus(OutboxEvents.Status.DEAD_LETTER);
                        System.err.println("[POLLER] DEAD_LETTER: " + evento.getId() + " após " + evento.getTentativas() + " tentativas");
                        backoffMap.remove(evento.getId());
                    } else {
                        evento.setStatus(OutboxEvents.Status.FAILED);
                        registrarBackOff(evento.getId(),evento.getTentativas());
                        System.err.println("[POLLER] FAILED (" + evento.getTentativas() + "/" + maxTentativas + "): " + evento.getId());
                    }
                    repository.save(evento);
                }
            }
        }

    }

    private boolean prontoParaRetry(OutboxEvents events){
        Instant proximo = backoffMap.get(events.getId());
        return  proximo == null || Instant.now().isAfter(proximo);
    }

    private void  registrarBackOff(UUID id, int tentativas){
        long ms = Math.min(1000L *(1L << tentativas), 30_000L);
        long jitter = (long) (Math.random() * ms);
        backoffMap.put(id, Instant.now().plusMillis(jitter));

    }

    private boolean idempotencia(List<OutboxEvents> eventos){
        for (OutboxEvents evento : eventos) {
            try {
                evento.setStatus(OutboxEvents.Status.PROCESSING);
                repository.save(evento);
                System.out.println("[POLLER] PROCESSING: " + evento.getId());

            } catch (Exception e) {
                evento.setStatus(OutboxEvents.Status.FAILED);
                System.err.println("[POLLER] FAILED (" + evento.getId());

                return  false;
            }
        }
        return  true;

    }


}
