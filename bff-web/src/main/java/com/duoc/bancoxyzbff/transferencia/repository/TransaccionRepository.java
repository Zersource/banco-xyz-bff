package com.duoc.bancoxyzbff.transferencia.repository;

import com.duoc.bancoxyzbff.transferencia.model.Transaccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// Nombre de bean explicito: ya existe com.duoc.bancoxyzbff.repository.TransaccionRepository
// (transacciones en memoria) y el nombre por defecto ("transaccionRepository") choca con ese.
@Repository("transferenciaTransaccionRepository")
public interface TransaccionRepository extends JpaRepository<Transaccion, Long> {
}
