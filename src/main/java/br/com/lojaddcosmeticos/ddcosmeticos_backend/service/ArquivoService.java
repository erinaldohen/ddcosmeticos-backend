package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class ArquivoService {

    // Se não houver nada no application.properties, usa "./uploads" por padrão
    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    private Path diretorioPath;

    @PostConstruct // Executa logo após a injeção de dependência do Spring
    public void init() {
        try {
            this.diretorioPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(this.diretorioPath);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao criar pasta de uploads em: " + uploadDir, e);
        }
    }

    public String salvarImagem(MultipartFile arquivo) {
        if (arquivo.isEmpty()) {
            throw new RuntimeException("Arquivo vazio.");
        }

        try {
            // Limpa o nome do arquivo para evitar ataques de Path Traversal (../)
            String nomeOriginal = arquivo.getOriginalFilename();
            if (nomeOriginal == null) nomeOriginal = "imagem";

            String extensao = "";
            int i = nomeOriginal.lastIndexOf('.');
            if (i > 0) extensao = nomeOriginal.substring(i);

            String nomeGerado = UUID.randomUUID().toString() + extensao;
            Path destino = this.diretorioPath.resolve(nomeGerado);

            Files.copy(arquivo.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);

            return nomeGerado;
        } catch (IOException e) {
            throw new RuntimeException("Erro ao salvar arquivo físico.", e);
        }
    }
}