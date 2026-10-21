package faker.data.scheme;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FakerSchemeRepository extends JpaRepository<FakerScheme, Long> {
    Optional<FakerScheme> findByName(String name);
}
