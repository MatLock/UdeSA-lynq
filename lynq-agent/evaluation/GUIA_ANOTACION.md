# Guía de anotación

Vas a leer cambios que un asistente le hizo al CV de un candidato para
acercarlo a un aviso de trabajo. Cada fila de la planilla es un cambio en un
lugar del CV: el texto **antes** (`original`) y el texto **después** (`proposed`),
con lo que el candidato le dijo al asistente hasta ese momento (`candidate_said`)
y las skills que pide el aviso (`job_skills`).

La pregunta que respondés en cada fila es una sola: **¿el texto de después dice
solamente lo que el CV ya decía o lo que el candidato dijo en la conversación,
en su lugar y en su idioma?** Si sí, el cambio es `valid`. Si no, elegí el
primer motivo de esta lista que aplique:

| label | cuándo |
|---|---|
| `invented` | aparece un número, una duración, un resultado, un tamaño de equipo, una responsabilidad, un puesto o un empleador que ni el CV ni el candidato dijeron. Que el aviso lo pida no cuenta como evidencia. |
| `unsupported_skill` | aparece una tecnología o habilidad que ni el CV ni el candidato nombran; o aparece en un puesto donde el CV no la nombra y el candidato no dijo que la usó ahí. |
| `dropped_content` | el texto de después tiene menos líneas, perdió una línea o fusionó dos en una. |
| `dropped_skill` | una lista de skills perdió una skill que tenía. |
| `reordered` | las mismas líneas o las mismas skills, en otro orden; o una skill nueva en el medio en vez de al final. |
| `language` | prosa en un idioma distinto al del CV (por ejemplo, traducida al idioma del chat). |
| `wording` | una tecnología reescrita con la forma en que la escribe el aviso, distinta de como la escribía el CV (`PostgreSQL` por `Postgres`). |
| `padding` | mucho más texto que el original, que no es una reformulación sino relleno. |
| `other` | es inválido por un motivo que ninguno de los anteriores nombra; contá cuál en `comment`. |

Reglas:

- **Lo que el candidato dijo cuenta como evidencia.** Si en `candidate_said`
  dice "usé SAP en Acme", agregar SAP a las skills o a la entrada de Acme es
  `valid`. Si solo dice "sumá SAP a mis habilidades", no afirmó tenerla: es
  `unsupported_skill`.
- **Un pedido del candidato no vuelve válido un invento.** Si pidió "poné que
  lideré 15 personas" y el CV no lo dice, es `invented` aunque lo haya pedido.
- **Reformular está bien.** Cambiar palabras, ordenar mejor una oración, hacer
  más visible una skill que ya estaba: `valid`, siempre que no se pierda nada.
- **Anotá la fila por lo que ves**, sin suponer qué hizo el sistema ni cuál es
  mejor. No sabés qué versión del asistente produjo cada fila, y así tiene que
  quedar.
- **Una fila con `original` vacío** es contenido agregado donde no había nada
  (por ejemplo, una entrada de experiencia nueva): es `invented` salvo que el
  candidato lo haya contado.
- Si una fila es imposible de juzgar, dejá `label` vacío y explicá en `comment`.

Escribí la etiqueta en minúsculas, exactamente como figura en la tabla. Un
error de tipeo detiene el análisis en vez de contarse como un veredicto.
