package com.ribeiry.outboxEvent;

import com.ribeiry.kafka.KafkaPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OutboxPoller {

    private final OutboxEventsRepository repository;
    private final KafkaPublisher kafkaPublisher;
    private final int maxTentativas;
    @Value("${outbox.time.to-reprocessing:15000}")
    public int Timereprocessing;


    public OutboxPoller(OutboxEventsRepository repository, KafkaPublisher kafkaPublisher,
                        @Value("${outbox.max-tentativas:3}") int maxTentativas) {
        this.repository = repository;
        this.kafkaPublisher = kafkaPublisher;
        this.maxTentativas = maxTentativas;

    }

    @Scheduled(fixedDelay = 5000)
    public void poll() {
        processar(repository.findByStatus(OutboxEvents.Status.PENDING));
        processar(repository.findByStatusAndTentativasLessThan(OutboxEvents.Status.FAILED, maxTentativas));
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
                    } else {
                        evento.setStatus(OutboxEvents.Status.FAILED);
                        System.err.println("[POLLER] FAILED (" + evento.getTentativas() + "/" + maxTentativas + "): " + evento.getId());
                    }
                    repository.save(evento);
                }
            }
        }

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
