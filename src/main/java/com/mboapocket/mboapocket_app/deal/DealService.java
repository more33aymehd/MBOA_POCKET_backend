package com.mboapocket.mboapocket_app.deal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mboapocket.mboapocket_app.deal.dto.DealRequest;
import com.mboapocket.mboapocket_app.deal.dto.DealResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DealService {

    private final DealRepository dealRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${groq.api.key}")
    private String groqApiKey;

    @Value("${groq.model}")
    private String groqModel;

    // ── Seed deals Yaoundé au démarrage si la table est vide ──
    @PostConstruct
    void seedDeals() {
        if (dealRepo.count() > 0) return;

        List<Deal> seeds = List.of(
            Deal.builder().titre("Pizza Palace").description("Réduction 20% après 18h").categorie("RESTOS")
                .icone("🍕").latitude(3.8667).longitude(11.5167).rayon(5000)
                .reduction("20%").rating(4.8).nbAvis(170).adresse("Bastos, Yaoundé")
                .expiration(LocalDate.now().plusMonths(3)).build(),

            Deal.builder().titre("Pharmacie du Centre").description("Achetez 3, payez 2").categorie("SANTE")
                .icone("💊").latitude(3.8650).longitude(11.5150).rayon(3000)
                .reduction("33%").rating(4.8).nbAvis(45).adresse("Centre-ville, Yaoundé")
                .expiration(LocalDate.now().plusMonths(1)).build(),

            Deal.builder().titre("Coupe Prestige").description("10% pour clients fidèles").categorie("SERVICES")
                .icone("✂️").latitude(3.8680).longitude(11.5190).rayon(2000)
                .reduction("10%").rating(5.0).nbAvis(12).adresse("Melen, Yaoundé")
                .expiration(LocalDate.now().plusMonths(2)).build(),

            Deal.builder().titre("Supermarché Score").description("Promotions weekend -15%").categorie("SHOPPING")
                .icone("🛒").latitude(3.8700).longitude(11.5200).rayon(4000)
                .reduction("15%").rating(4.5).nbAvis(230).adresse("Nlongkak, Yaoundé")
                .expiration(LocalDate.now().plusWeeks(2)).build(),

            Deal.builder().titre("Restaurant Le Terroir").description("Menu du jour 2500 FCFA").categorie("RESTOS")
                .icone("🍽️").latitude(3.8620).longitude(11.5120).rayon(3000)
                .reduction("Menu spécial").rating(4.6).nbAvis(88).adresse("Omnisports, Yaoundé")
                .expiration(LocalDate.now().plusMonths(1)).build(),

            Deal.builder().titre("MTN Shop").description("Recharge + 20% de bonus").categorie("SERVICES")
                .icone("📱").latitude(3.8690).longitude(11.5170).rayon(5000)
                .reduction("+20% bonus").rating(4.3).nbAvis(55).adresse("Mvog-Ada, Yaoundé")
                .expiration(LocalDate.now().plusWeeks(3)).build(),

            Deal.builder().titre("Librairie Nationale").description("Rentrée : -25% fournitures").categorie("SHOPPING")
                .icone("📚").latitude(3.8640).longitude(11.5140).rayon(3000)
                .reduction("25%").rating(4.7).nbAvis(33).adresse("Centre admin, Yaoundé")
                .expiration(LocalDate.now().plusMonths(2)).build()
        );

        dealRepo.saveAll(seeds);
    }

    public List<DealResponse> getNearby(double lat, double lng, double rayonKm) {
        List<Deal> deals = dealRepo.findNearby(lat, lng, rayonKm);
        return deals.stream().map(d -> toResponse(d, lat, lng)).collect(Collectors.toList());
    }

    public List<DealResponse> getNearbyByCategorie(double lat, double lng, double rayonKm, String categorie) {
        return getNearby(lat, lng, rayonKm).stream()
                .filter(d -> categorie.equalsIgnoreCase(d.getCategorie()))
                .collect(Collectors.toList());
    }

    public DealResponse create(DealRequest req) {
        Deal deal = Deal.builder()
                .titre(req.getTitre())
                .description(req.getDescription())
                .categorie(req.getCategorie())
                .icone(req.getIcone() != null ? req.getIcone() : "🏷️")
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .rayon(req.getRayon() != null ? req.getRayon() : 1000)
                .expiration(req.getExpiration())
                .reduction(req.getReduction())
                .rating(req.getRating() != null ? req.getRating() : 4.0)
                .nbAvis(req.getNbAvis() != null ? req.getNbAvis() : 0)
                .adresse(req.getAdresse())
                .build();
        return toResponse(dealRepo.save(deal), 0, 0);
    }

    public List<DealResponse> getAll(double lat, double lng) {
        return dealRepo.findAll().stream()
                .map(d -> toResponse(d, lat, lng))
                .sorted(Comparator.comparingDouble(DealResponse::getRating).reversed())
                .collect(Collectors.toList());
    }

    public List<DealResponse> getNearbySortedByAI(double lat, double lng, double rayonKm) {
        List<DealResponse> deals = getNearby(lat, lng, rayonKm);
        if (deals.isEmpty()) return deals;
        try {
            return rankByAI(deals);
        } catch (Exception e) {
            // Fallback : tri par réduction décroissante si l'IA échoue
            return deals.stream()
                .sorted(Comparator.comparingDouble(DealService::parseReduction).reversed())
                .collect(Collectors.toList());
        }
    }

    private List<DealResponse> rankByAI(List<DealResponse> deals) throws Exception {
        StringBuilder dealsJson = new StringBuilder("[");
        for (int i = 0; i < deals.size(); i++) {
            DealResponse d = deals.get(i);
            dealsJson.append(String.format(
                "{\"id\":%d,\"titre\":\"%s\",\"reduction\":\"%s\",\"distanceKm\":%.1f,\"rating\":%.1f,\"categorie\":\"%s\"}",
                d.getId(), d.getTitre(), d.getReduction() != null ? d.getReduction() : "",
                d.getDistanceKm(), d.getRating(), d.getCategorie()
            ));
            if (i < deals.size() - 1) dealsJson.append(",");
        }
        dealsJson.append("]");

        String prompt = "Tu es un assistant financier pour l'application Mboapocket au Cameroun. " +
            "Voici une liste de bons plans à proximité de l'utilisateur : " + dealsJson +
            "\nClasse-les du meilleur au moins bon en tenant compte : 1) du pourcentage de réduction " +
            "(plus c'est élevé, mieux c'est), 2) de la distance (plus c'est proche, mieux c'est), " +
            "3) du rating. Retourne UNIQUEMENT un tableau JSON sans explication, format strict : " +
            "[{\"id\":1,\"score\":1,\"raison\":\"Meilleure réduction (-30%) et proche\"},...] " +
            "score=1 étant le meilleur deal. Réponds uniquement avec le JSON.";

        String requestBody = mapper.writeValueAsString(Map.of(
            "model", groqModel,
            "messages", List.of(Map.of("role", "user", "content", prompt)),
            "temperature", 0.2,
            "max_tokens", 800
        ));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
            .header("Authorization", "Bearer " + groqApiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());

        JsonNode root = mapper.readTree(response.body());
        String content = root.path("choices").get(0).path("message").path("content").asText();

        // Extraire le JSON du contenu
        int start = content.indexOf('[');
        int end = content.lastIndexOf(']') + 1;
        if (start == -1 || end == 0) return deals;

        JsonNode aiRanking = mapper.readTree(content.substring(start, end));
        Map<Long, DealResponse> dealsById = deals.stream()
            .collect(Collectors.toMap(DealResponse::getId, d -> d));

        List<DealResponse> sorted = new ArrayList<>();
        for (JsonNode node : aiRanking) {
            long id = node.path("id").asLong();
            DealResponse deal = dealsById.get(id);
            if (deal != null) {
                deal.setAiScore(node.path("score").asInt());
                deal.setAiRaison(node.path("raison").asText());
                sorted.add(deal);
            }
        }
        // Ajouter les deals non retournés par l'IA en fin de liste
        deals.stream().filter(d -> sorted.stream().noneMatch(s -> s.getId().equals(d.getId())))
            .forEach(sorted::add);
        return sorted;
    }

    private static double parseReduction(DealResponse d) {
        if (d.getReduction() == null) return 0;
        try {
            return Double.parseDouble(d.getReduction().replace("%", "").replace("+", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public DealResponse getById(Long id) {
        return dealRepo.findById(id)
                .map(d -> toResponse(d, 0, 0))
                .orElseThrow(() -> new IllegalArgumentException("Deal introuvable"));
    }

    private DealResponse toResponse(Deal d, double userLat, double userLng) {
        double dist = 0;
        if (userLat != 0 && userLng != 0) {
            dist = haversine(userLat, userLng, d.getLatitude(), d.getLongitude());
        }
        return DealResponse.builder()
                .id(d.getId())
                .titre(d.getTitre())
                .description(d.getDescription())
                .categorie(d.getCategorie())
                .icone(d.getIcone())
                .latitude(d.getLatitude())
                .longitude(d.getLongitude())
                .reduction(d.getReduction())
                .expiration(d.getExpiration())
                .rating(d.getRating())
                .nbAvis(d.getNbAvis())
                .adresse(d.getAdresse())
                .distanceKm(Math.round(dist * 10.0) / 10.0)
                .build();
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
