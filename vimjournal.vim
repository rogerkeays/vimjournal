"
autocmd BufRead,BufNewFile *.log setl filetype=journal

autocmd FileType journal setl foldmethod=expr foldtext=getline(v:foldstart) fillchars= 
autocmd FileType journal setl foldexpr=getline(v\:lnum)=~'^.\\{57\\}│'?'>1'\:1
"autocmd FileType journal setl foldexpr=getline(v\:lnum+1)=~'^.\\{8\\}..\\{4\\}\ .\\{43\\}│'?'<1'\:1
"autocmd FileType journal setl foldexpr=getline(v\:lnum)=~'^.\\{8\\}[\ \.].\\{4\\}\ .\\{43\\}│'?getline(v\:lnum)=~'│\ \ '?'>2'\:'>1'\:'='
autocmd FileType journal setl autoindent sw=2 ts=8 nrformats= 
autocmd FileType journal setl wrap linebreak breakindent showbreak=>\ 

autocmd FileType journal nnoremap <TAB> za
autocmd FileType journal nnoremap <C-t> Go<C-R>=strftime("%Y%m%d_%H%M")<CR> KEP .                   .                  │ <ESC>zm1GGA
autocmd FileType journal nnoremap <C-o> yyp:s/│ .*/│ <CR>A
autocmd FileType journal nnoremap <C-l> :Explore /home/octopus/journal/history<CR>
autocmd FileType journal inoremap <C-t> // <C-R>=strftime("%Y%m%d_%H%M")<CR> 
autocmd FileType journal inoremap <C-b> │
autocmd FileType journal inoremap <C-x> ✘
autocmd FileType journal inoremap <C-z> ✔

autocmd BufRead *.log syn match NoReview "^.*[ ?]│.*$"
autocmd BufRead *.log syn match Worst "^.*[1x/]│.*$"
autocmd BufRead *.log syn match Bad "^.*[2-]│.*$"
autocmd BufRead *.log syn match Okay "^.*[3~=]│.*$"
autocmd BufRead *.log syn match Good "^.*[4+·]│.*$"
autocmd BufRead *.log syn match Best "^.*[5*]│.*$"
autocmd BufRead *.log syn match Heading "^==[^ ].*$"
autocmd BufRead *.log syn match Heading "^## .*$"
autocmd BufRead *.log syn match Comment "^//.*$"
autocmd BufRead *.log syn match Comment " // .*$"
"autocmd BufRead *.log syn match Reference " --[A-Za-z].*$"

autocmd BufRead *.log hi NoReview ctermfg=lightgrey
autocmd BufRead *.log hi Worst ctermfg=darkred ctermbg=black
autocmd BufRead *.log hi Bad ctermfg=red
autocmd BufRead *.log hi Okay ctermfg=darkcyan
autocmd BufRead *.log hi Good ctermfg=darkgreen
autocmd BufRead *.log hi Best ctermfg=green ctermbg=black
autocmd BufRead *.log hi Heading ctermfg=white
autocmd BufRead *.log hi Comment ctermfg=lightgreen
autocmd BufRead *.log hi Reference ctermfg=lightyellow

" recognise _ in datetimes as a word boundary
set iskeyword-=_

