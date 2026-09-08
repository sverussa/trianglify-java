# Avisos de terceiros

Este projeto é um trabalho derivado: boa parte do código é um porte, para Java, de bibliotecas
JavaScript existentes. Como o Trianglify é licenciado sob a GPL-3.0, o projeto como um todo é
distribuído sob a GPL-3.0 — veja [LICENSE](LICENSE).

Abaixo, a origem de cada parte e a licença correspondente.

---

## Trianglify

Origem do algoritmo e do desenho geral da API. Portado em `Trianglify.java`, `Pattern.java`,
`Options.java`, `Poly.java` e `color/ColorFunctions.java`.

- Projeto: https://github.com/qrohlf/trianglify (v4.1.1)
- Copyright © Quinn Rohlf
- Licença: GNU General Public License v3.0 — texto completo em [LICENSE](LICENSE)

---

## Delaunator

A triangulação de Delaunay em `Delaunator.java` é um porte direto do Delaunator v4.0.1.

- Projeto: https://github.com/mapbox/delaunator
- Licença: ISC

```
ISC License

Copyright (c) 2017, Mapbox

Permission to use, copy, modify, and/or distribute this software for any purpose
with or without fee is hereby granted, provided that the above copyright notice
and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS
OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER
TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF
THIS SOFTWARE.
```

---

## chroma.js

As conversões sRGB ↔ CIE L\*a\*b\*, a escala de gradiente e a serialização CSS em
`color/ChromaColor.java` e `color/ColorScale.java` são portes do chroma-js 2.1.0.

- Projeto: https://github.com/gka/chroma.js
- Copyright © 2011-2019, Gregor Aisch
- Licença: BSD 3-Clause

```
chroma.js - JavaScript library for color conversions

Copyright (c) 2011-2019, Gregor Aisch
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. The name Gregor Aisch may not be used to endorse or promote products
   derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL GREGOR AISCH OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

---

## ColorBrewer

As 27 paletas em `color/ColorBrewer.java` são as especificações de cor do ColorBrewer,
reproduzidas a partir do Trianglify.

- Projeto: https://colorbrewer2.org
- Copyright © 2002 Cynthia Brewer, Mark Harrower e The Pennsylvania State University
- Licença: Apache License 2.0

```
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
either express or implied. See the License for the specific
language governing permissions and limitations under the License.
```

---

## mulberry32

O gerador pseudo-aleatório em `util/Mulberry32.java` é um porte da implementação de domínio
público de @bryc, chegada aqui pelo Trianglify.

- Origem: https://github.com/bryc/code/blob/master/jshash/PRNGs.md
- Domínio público

---

## Gradle Wrapper

Os arquivos `gradlew`, `gradlew.bat` e `gradle/wrapper/` são distribuídos junto com o projeto,
como recomenda o próprio Gradle.

- Projeto: https://github.com/gradle/gradle (v9.7.1)
- Copyright © Gradle Inc.
- Licença: Apache License 2.0 — https://www.apache.org/licenses/LICENSE-2.0
